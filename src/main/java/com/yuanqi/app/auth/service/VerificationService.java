package com.yuanqi.app.auth.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yuanqi.app.auth.entity.VerificationFlow;
import com.yuanqi.app.auth.entity.VerificationGeneration;
import com.yuanqi.app.auth.mail.MailPort;
import com.yuanqi.app.auth.mapper.AccountMapper;
import com.yuanqi.app.auth.mapper.VerificationFlowMapper;
import com.yuanqi.app.auth.mapper.VerificationGenerationMapper;
import com.yuanqi.app.auth.support.CryptoSupport;
import com.yuanqi.app.auth.support.EmailNormalizer;
import com.yuanqi.app.auth.support.PublicIdGenerator;
import com.yuanqi.app.auth.support.VerificationCodeGenerator;
import com.yuanqi.app.auth.vo.AuthViews.VerificationFlowView;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.api.ErrorResult.VerificationErrorContext;
import com.yuanqi.app.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class VerificationService {
    private final VerificationFlowMapper flowMapper;
    private final VerificationGenerationMapper generationMapper;
    private final AccountMapper accountMapper;
    private final AuthRateLimiter rateLimiter;
    private final EmailNormalizer emailNormalizer;
    private final VerificationCodeGenerator codeGenerator;
    private final PublicIdGenerator idGenerator;
    private final CryptoSupport crypto;
    private final MailPort mailPort;
    private final Clock clock;

    public VerificationService(VerificationFlowMapper flowMapper,
                               VerificationGenerationMapper generationMapper,
                               AccountMapper accountMapper,
                               AuthRateLimiter rateLimiter,
                               EmailNormalizer emailNormalizer,
                               VerificationCodeGenerator codeGenerator,
                               PublicIdGenerator idGenerator,
                               CryptoSupport crypto,
                               MailPort mailPort,
                               Clock clock) {
        this.flowMapper = flowMapper;
        this.generationMapper = generationMapper;
        this.accountMapper = accountMapper;
        this.rateLimiter = rateLimiter;
        this.emailNormalizer = emailNormalizer;
        this.codeGenerator = codeGenerator;
        this.idGenerator = idGenerator;
        this.crypto = crypto;
        this.mailPort = mailPort;
        this.clock = clock;
    }

    @Transactional
    public VerificationFlowView sendCode(String rawEmail, String ip) {
        String emailKey = emailNormalizer.normalize(rawEmail);
        if (accountMapper.findByEmailKey(emailKey) != null) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        rateLimiter.checkVerificationSend(emailKey, ip);
        LocalDateTime now = now();
        VerificationFlow flow = flowMapper.findActiveByEmailForUpdate(emailKey);
        if (flow != null && !flow.getExpiresAt().isAfter(now)) {
            flow.setStatus("EXPIRED");
            flow.setRowVersion(flow.getRowVersion() + 1);
            flowMapper.updateById(flow);
            flow = null;
        }
        if (flow == null) {
            flow = new VerificationFlow();
            flow.setFlowId(idGenerator.next());
            flow.setEmailKey(emailKey);
            flow.setEmailAddress(emailKey);
            flow.setPurpose("REGISTER");
            flow.setStatus("ACTIVE");
            flow.setFailedAttempts(0);
            flow.setActiveGeneration(0);
            flow.setStartedAt(now);
            flow.setExpiresAt(now.plusMinutes(10));
            flow.setRowVersion(0L);
            flowMapper.insert(flow);
        }

        VerificationGeneration previous = flow.getActiveGeneration() == 0 ? null
                : generationMapper.findForUpdate(flow.getId(), flow.getActiveGeneration());
        if (previous != null && previous.getSentAt() != null && previous.getSentAt().plusSeconds(60).isAfter(now)) {
            int retry = ceilSeconds(now, previous.getSentAt().plusSeconds(60));
            throw new BusinessException(ErrorCode.RESEND_TOO_SOON, ErrorCode.RESEND_TOO_SOON.getMessage(), true, retry);
        }

        int generationNo = flow.getActiveGeneration() + 1;
        String code = codeGenerator.generateSixDigits();
        VerificationGeneration generation = new VerificationGeneration();
        generation.setFlowId(flow.getId());
        generation.setGeneration(generationNo);
        generation.setCodeHmac(crypto.hmac(flow.getFlowId() + ":" + generationNo + ":" + code));
        generation.setStatus("SENDING");
        generation.setExpiresAt(flow.getExpiresAt());
        generation.setCreatedAt(now);
        generationMapper.insert(generation);

        mailPort.sendRegistrationCode(flow.getEmailAddress(), code);
        if (previous != null) {
            previous.setStatus("SUPERSEDED");
            generationMapper.updateById(previous);
        }
        generation.setStatus("SENT");
        generation.setSentAt(now);
        generationMapper.updateById(generation);
        flow.setActiveGeneration(generationNo);
        flow.setRowVersion(flow.getRowVersion() + 1);
        flowMapper.updateById(flow);
        return view(flow, now.plusSeconds(60));
    }

    /** 在调用方注册事务中锁定并校验 Flow，成功后必须调用 consume。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = VerificationException.class)
    public VerificationFlow verifyForRegistration(String flowId, String rawEmail, String code, String ip) {
        String emailKey = emailNormalizer.normalize(rawEmail);
        VerificationFlow flow = flowMapper.findByFlowIdForUpdate(flowId);
        if (flow == null || !flow.getEmailKey().equals(emailKey)) {
            throw new BusinessException(ErrorCode.VERIFICATION_FLOW_CONSUMED);
        }
        LocalDateTime now = now();
        if ("CONSUMED".equals(flow.getStatus())) {
            throw new BusinessException(ErrorCode.VERIFICATION_FLOW_CONSUMED);
        }
        if ("EXHAUSTED".equals(flow.getStatus())) {
            throw exhausted(flow, now);
        }
        if (!flow.getExpiresAt().isAfter(now) || "EXPIRED".equals(flow.getStatus())) {
            if (!"EXPIRED".equals(flow.getStatus())) {
                flow.setStatus("EXPIRED");
                flow.setRowVersion(flow.getRowVersion() + 1);
                flowMapper.updateById(flow);
            }
            throw expired(flow, now, ip);
        }
        VerificationGeneration generation = generationMapper.findForUpdate(flow.getId(), flow.getActiveGeneration());
        String supplied = crypto.hmac(flow.getFlowId() + ":" + flow.getActiveGeneration() + ":" + code);
        if (generation == null || !"SENT".equals(generation.getStatus())
                || !crypto.constantTimeEquals(generation.getCodeHmac(), supplied)) {
            int attempts = flow.getFailedAttempts() + 1;
            flow.setFailedAttempts(attempts);
            flow.setRowVersion(flow.getRowVersion() + 1);
            if (attempts >= 5) {
                flow.setStatus("EXHAUSTED");
                flowMapper.updateById(flow);
                throw exhausted(flow, now);
            }
            flowMapper.updateById(flow);
            throw new VerificationException(ErrorCode.VERIFICATION_CODE_INVALID, true, null,
                    new VerificationErrorContext(5 - attempts, utc(flow.getExpiresAt()), null, false));
        }
        return flow;
    }

    public void consume(VerificationFlow flow) {
        flow.setStatus("CONSUMED");
        flow.setConsumedAt(now());
        flow.setRowVersion(flow.getRowVersion() + 1);
        flowMapper.updateById(flow);
    }

    private VerificationException exhausted(VerificationFlow flow, LocalDateTime now) {
        int retry = ceilSeconds(now, flow.getExpiresAt());
        return new VerificationException(ErrorCode.VERIFICATION_CODE_EXHAUSTED, true, retry,
                new VerificationErrorContext(0, utc(flow.getExpiresAt()), utc(flow.getExpiresAt()), true));
    }

    private VerificationException expired(VerificationFlow flow, LocalDateTime now, String ip) {
        VerificationGeneration generation = flow.getActiveGeneration() == 0 ? null
                : generationMapper.findForUpdate(flow.getId(), flow.getActiveGeneration());
        LocalDateTime retryAt = later(now, flow.getExpiresAt());
        if (generation != null && generation.getSentAt() != null) {
            retryAt = later(retryAt, generation.getSentAt().plusSeconds(60));
        }
        retryAt = later(retryAt, rateLimiter.nextVerificationSendAt(flow.getEmailKey(), ip));
        Integer retry = retryAt.equals(now) ? null : ceilSeconds(now, retryAt);
        return new VerificationException(ErrorCode.VERIFICATION_CODE_EXPIRED, true, retry,
                new VerificationErrorContext(0, utc(flow.getExpiresAt()), utc(retryAt), true));
    }

    private VerificationFlowView view(VerificationFlow flow, LocalDateTime resendAt) {
        return new VerificationFlowView(flow.getFlowId(), utc(flow.getExpiresAt()), utc(resendAt),
                5 - flow.getFailedAttempts());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private OffsetDateTime utc(LocalDateTime value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    private LocalDateTime later(LocalDateTime left, LocalDateTime right) {
        return right != null && right.isAfter(left) ? right : left;
    }

    private int ceilSeconds(LocalDateTime from, LocalDateTime to) {
        long millis = Math.max(1, Duration.between(from, to).toMillis());
        return (int) Math.max(1, (millis + 999) / 1000);
    }
}
