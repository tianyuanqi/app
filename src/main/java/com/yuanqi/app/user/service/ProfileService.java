package com.yuanqi.app.user.service;

import com.yuanqi.app.auth.entity.Account;
import com.yuanqi.app.auth.mapper.AccountMapper;
import com.yuanqi.app.auth.support.AuthPolicy;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.common.text.UnicodeText;
import com.yuanqi.app.user.dto.ProfileRequests;
import com.yuanqi.app.user.entity.UserProfile;
import com.yuanqi.app.user.mapper.UserProfileMapper;
import com.yuanqi.app.user.vo.ProfileViews;
import com.yuanqi.app.photo.mapper.MediaAssetMapper;
import com.yuanqi.app.photo.entity.MediaAsset;
import com.yuanqi.app.photo.vo.MediaViews;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;

@Service
public class ProfileService {
    private static final ZoneId PRODUCT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "OTHER", "UNDISCLOSED");
    private final AccountMapper accountMapper;
    private final UserProfileMapper profileMapper;
    private final Clock clock;
    private final MediaAssetMapper mediaMapper;

    public ProfileService(AccountMapper accountMapper, UserProfileMapper profileMapper, Clock clock,MediaAssetMapper mediaMapper) {
        this.accountMapper = accountMapper;
        this.profileMapper = profileMapper;
        this.clock = clock;
        this.mediaMapper=mediaMapper;
    }

    @Transactional(readOnly = true)
    public ProfileViews.PrivateProfile privateProfile(Long accountId) {
        Account account = requireAccount(accountId);
        UserProfile profile = requireProfile(accountId);
        return privateView(account, profile);
    }

    @Transactional
    public ProfileViews.PrivateProfile update(Long accountId, String ifMatch, ProfileRequests.Update request) {
        Account account = requireAccount(accountId);
        if ("DISABLED".equals(account.getGovernanceStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_UNAVAILABLE);
        }
        UserProfile profile = profileMapper.findForUpdate(accountId);
        requireMatch(ifMatch, profile.getRowVersion());
        profile.setUsername(AuthPolicy.validateUsername(request.username()));
        profile.setBio(validateBio(request.bio()));
        profile.setBirthDate(validateBirthDate(request.birthDate()));
        profile.setGender(validateGender(request.gender()));
        profile.setRowVersion(profile.getRowVersion() + 1);
        profile.setUpdatedAt(nowUtc());
        profileMapper.updateById(profile);
        return privateView(account, profile);
    }

    @Transactional(readOnly = true)
    public ProfileViews.PublicProfile publicProfile(String uid) {
        Account account = accountMapper.findByUid(uid);
        if (account == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        UserProfile profile = requireProfile(account.getId());
        String publicGender = "UNDISCLOSED".equals(profile.getGender()) ? null : profile.getGender();
        return new ProfileViews.PublicProfile(account.getUid(), profile.getUsername(), profile.getBio(),
                age(profile.getBirthDate()), publicGender, avatar(profile), account.getCreatedAt().atOffset(ZoneOffset.UTC),
                profileMapper.countPublicWorks(account.getId()), profileMapper.countReceivedLikes(account.getId()));
    }

    public String versionTag(long version) {
        return "\"profile-" + version + "\"";
    }

    private ProfileViews.PrivateProfile privateView(Account account, UserProfile profile) {
        return new ProfileViews.PrivateProfile(account.getUid(), account.getEmail(), profile.getUsername(),
                profile.getBio(), profile.getBirthDate(), profile.getGender(), avatar(profile),
                account.getCreatedAt().atOffset(ZoneOffset.UTC), versionTag(profile.getRowVersion()));
    }

    private String validateBio(String input) {
        if (input == null) return null;
        String value = UnicodeText.nfc(UnicodeText.trimUnicode(input.replace("\r\n", "\n")));
        if (value.isEmpty()) return null;
        if (UnicodeText.graphemeCount(value) > 200 || value.lines().count() > 3
                || UnicodeText.containsForbiddenControl(value, true)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "个性签名最多 200 个可见字符和三行");
        }
        return value;
    }

    private LocalDate validateBirthDate(LocalDate value) {
        if (value != null && value.isAfter(LocalDate.now(clock.withZone(PRODUCT_ZONE)))) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "生日不能晚于今天");
        }
        return value;
    }

    private String validateGender(String value) {
        if (value != null && !GENDERS.contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "性别值无效");
        }
        return value;
    }

    private Integer age(LocalDate birthDate) {
        if (birthDate == null) return null;
        LocalDate today = LocalDate.now(clock.withZone(PRODUCT_ZONE));
        LocalDate anniversary = birthDate.getMonthValue() == 2 && birthDate.getDayOfMonth() == 29
                && !today.isLeapYear()
                ? LocalDate.of(today.getYear(), 3, 1) : birthDate.withYear(today.getYear());
        int age = today.getYear() - birthDate.getYear();
        return today.isBefore(anniversary) ? age - 1 : age;
    }

    private void requireMatch(String ifMatch, long current) {
        if (ifMatch == null || ifMatch.isBlank()) throw new BusinessException(ErrorCode.PRECONDITION_REQUIRED);
        if (!versionTag(current).equals(ifMatch)) throw new BusinessException(ErrorCode.PRECONDITION_FAILED);
    }

    private Account requireAccount(Long id) {
        Account account = id == null ? null : accountMapper.selectById(id);
        if (account == null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        return account;
    }

    private UserProfile requireProfile(Long id) {
        UserProfile profile = profileMapper.selectById(id);
        if (profile == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return profile;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
    private MediaViews.WebMedia avatar(UserProfile profile){if(profile.getAvatarMediaId()==null)return null;MediaAsset a=mediaMapper.selectById(profile.getAvatarMediaId());if(a==null||!"READY".equals(a.getStatus()))return null;return new MediaViews.WebMedia(a.getMediaId(),"PUBLIC_URL","/api/v1/media/"+a.getMediaId()+"/web",a.getMimeType(),a.getWidth(),a.getHeight(),"\"media-"+a.getRowVersion()+"\"");}
}
