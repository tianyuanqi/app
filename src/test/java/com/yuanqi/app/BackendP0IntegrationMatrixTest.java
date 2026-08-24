package com.yuanqi.app;

import com.yuanqi.app.auth.entity.Account;
import com.yuanqi.app.auth.mapper.AccountMapper;
import com.yuanqi.app.auth.service.AuthSessionService;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.moderation.service.ReviewService;
import com.yuanqi.app.photo.dto.WorkRequests;
import com.yuanqi.app.photo.service.WorkService;
import com.yuanqi.app.photo.vo.WorkViews;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpHeaders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** V1-BE-GAP-003：只在专用测试 Schema 中执行的可重复 P0 持久化集成矩阵。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BackendP0IntegrationMatrixTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountMapper accounts;
    @Autowired AuthSessionService sessions;
    @Autowired WorkService works;
    @Autowired ReviewService reviews;
    @Autowired MockMvc mockMvc;

    @Test void 真实HTTPMutation返回新ETag且旧条件版本失败() throws Exception {
        Account account = accounts.selectById(account("uid_http_etag", "USER"));
        String token = sessions.issue(account).view().accessToken();
        String authorization = "Bearer " + token;
        String before = mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk()).andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        String body = "{\"username\":\"新名字\",\"bio\":\"简介\",\"gender\":\"UNDISCLOSED\"}";
        String after = mockMvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, authorization)
                        .header(HttpHeaders.IF_MATCH, before).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(header().exists(HttpHeaders.ETAG))
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(after).isNotEqualTo(before);
        mockMvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, authorization)
                        .header(HttpHeaders.IF_MATCH, before).contentType("application/json").content(body))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("PRECONDITION_FAILED"));
    }

    @Test void Session签发解析旋转退出形成完整闭环() {
        long accountId = account("uid_session", "USER");
        Account account = accounts.selectById(accountId);

        AuthSessionService.IssuedSession first = sessions.issue(account);
        assertThat(sessions.resolve(first.refreshCredential()).account().getUid()).isEqualTo("uid_session");
        AuthSessionService.IssuedSession second = sessions.rotate(first.refreshCredential());
        assertThat(second.view().sessionExpiresAt()).isEqualTo(first.view().sessionExpiresAt());
        assertThat(sessions.resolve(second.refreshCredential()).session().getStatus()).isEqualTo("ACTIVE");
        assertThat(sessions.logout(second.refreshCredential())).isFalse();
        assertError(ErrorCode.SESSION_INVALID, () -> sessions.resolve(second.refreshCredential()));
        assertThat(sessions.logout(second.refreshCredential())).isTrue();
    }

    @Test void Refresh重放吊销整个Session且旧凭证不可恢复() {
        Account account = accounts.selectById(account("uid_replay", "USER"));
        AuthSessionService.IssuedSession first = sessions.issue(account);
        AuthSessionService.IssuedSession second = sessions.rotate(first.refreshCredential());

        assertError(ErrorCode.REFRESH_REUSED, () -> sessions.rotate(first.refreshCredential()));
        assertError(ErrorCode.SESSION_INVALID, () -> sessions.resolve(second.refreshCredential()));
        assertThat(jdbc.queryForObject("SELECT status FROM auth_session WHERE session_id=?", String.class,
                second.view().accessToken() == null ? "" : sessionId(account.getId()))).isEqualTo("REVOKED");
    }

    @Test void 作品审核状态机公开版本替换与每次Mutation强ETag递增() {
        long adminId = account("uid_self_admin", "ADMIN");
        String mediaId = media(adminId, "media_state");
        WorkViews.AuthorWork created = works.create(adminId, draft("第一版", mediaId));
        String workId = created.summary().workId();
        String revision1 = created.workingRevision().revisionId();
        assertThat(created.summary().versionTag()).isEqualTo("\"work-0\"");

        WorkViews.AuthorWork pending1 = works.submit(adminId, workId, created.summary().versionTag());
        assertTagChanged(created, pending1);
        WorkViews.AuthorWork withdrawn = works.withdraw(adminId, workId, pending1.summary().versionTag());
        assertTagChanged(pending1, withdrawn);
        WorkViews.AuthorWork pendingAgain = works.submit(adminId, workId, withdrawn.summary().versionTag());
        var approved1 = reviews.approve(adminId, workId, revision1, pendingAgain.summary().versionTag());
        assertThat(approved1.resultingPublicationState()).isEqualTo("PUBLISHED");
        assertThat(approved1.versionTag()).isNotEqualTo(pendingAgain.summary().versionTag());
        assertThat(jdbc.queryForObject("SELECT self_review FROM moderation_event WHERE work_id=(SELECT id FROM photo_work WHERE work_id=?) ORDER BY id DESC LIMIT 1",
                Boolean.class, workId)).isTrue();

        WorkViews.AuthorWork edit = works.createDraft(adminId, workId, approved1.versionTag());
        WorkViews.AuthorWork changed = works.updateDraft(adminId, workId, edit.summary().versionTag(), draft("第二版", mediaId));
        WorkViews.AuthorWork pending2 = works.submit(adminId, workId, changed.summary().versionTag());
        var rejected = reviews.reject(adminId, workId, pending2.workingRevision().revisionId(),
                pending2.summary().versionTag(), "需要调整");
        assertThat(rejected.resultingWorkingState()).isEqualTo("REJECTED");
        WorkViews.AuthorWork rework = works.createDraft(adminId, workId, rejected.versionTag());
        WorkViews.AuthorWork pending3 = works.submit(adminId, workId, rework.summary().versionTag());
        var approved2 = reviews.approve(adminId, workId, pending3.workingRevision().revisionId(),
                pending3.summary().versionTag());

        assertThat(approved2.versionTag()).isNotEqualTo(pending3.summary().versionTag());
        assertThat(jdbc.queryForObject("SELECT r.revision_id FROM photo_work w JOIN photo_revision r ON r.id=w.public_revision_id WHERE w.work_id=?",
                String.class, workId)).isEqualTo(pending3.workingRevision().revisionId());
        assertThat(jdbc.queryForObject("SELECT state FROM photo_revision WHERE revision_id=?", String.class, revision1))
                .isEqualTo("SUPERSEDED");
    }

    @Test void 所有权防枚举与目标状态校验统一RESOURCE_NOT_FOUND() {
        long owner = account("uid_owner", "USER");
        long stranger = account("uid_stranger", "USER");
        WorkViews.AuthorWork created = works.create(owner, draft("私有草稿", media(owner, "media_private")));

        assertError(ErrorCode.RESOURCE_NOT_FOUND, () -> works.authorView(stranger, created.summary().workId()));
        assertError(ErrorCode.RESOURCE_NOT_FOUND, () -> works.draft(stranger, created.summary().workId()));
        assertError(ErrorCode.RESOURCE_NOT_FOUND,
                () -> reviews.target(stranger, created.summary().workId(), created.workingRevision().revisionId()));
        assertError(ErrorCode.RESOURCE_NOT_FOUND,
                () -> works.authorView(owner, "work_does_not_exist"));
    }

    @Test void 我的作品稳定分页使用更新时间与公开ID确定性排序() {
        long owner = account("uid_page", "USER");
        for (int i = 1; i <= 5; i++) works.create(owner, draft("分页" + i, media(owner, "media_page_" + i)));
        jdbc.update("UPDATE photo_work SET updated_at=? WHERE author_account_id=?", LocalDateTime.of(2026, 8, 24, 1, 0), owner);

        var first = works.mine(owner, 1, 2);
        var second = works.mine(owner, 2, 2);
        var replay = works.mine(owner, 1, 2);
        assertThat(first.items()).extracting(WorkViews.Summary::workId)
                .containsExactlyElementsOf(replay.items().stream().map(WorkViews.Summary::workId).toList());
        assertThat(first.items()).extracting(WorkViews.Summary::workId)
                .doesNotContainAnyElementsOf(second.items().stream().map(WorkViews.Summary::workId).toList());
        assertThat(first.totalItems()).isEqualTo(5);
    }

    @Test void 审核队列同提交时间按Revision公开ID稳定排序() {
        long owner = account("uid_queue", "USER");
        for (int i = 1; i <= 3; i++) {
            WorkViews.AuthorWork work = works.create(owner, draft("待审" + i, media(owner, "media_queue_" + i)));
            works.submit(owner, work.summary().workId(), work.summary().versionTag());
        }
        jdbc.update("UPDATE photo_revision SET submitted_at=? WHERE state='PENDING'", LocalDateTime.of(2026, 8, 24, 2, 0));
        var page = reviews.queue(1, 100);
        assertThat(page.items()).extracting(x -> x.revisionId()).isSorted();
        assertThat(page.totalItems()).isEqualTo(3);
    }

    private long account(String uid, String role) {
        jdbc.update("INSERT INTO user_account(uid,email,email_key,password_hash,role,governance_status,row_version,created_at,updated_at) VALUES(?,?,?,?,?,'ACTIVE',0,NOW(6),NOW(6))",
                uid, uid + "@example.invalid", uid + "@example.invalid", "test-hash", role);
        long id = jdbc.queryForObject("SELECT id FROM user_account WHERE uid=?", Long.class, uid);
        jdbc.update("INSERT INTO user_profile(account_id,username,row_version,updated_at) VALUES(?,?,0,NOW(6))", id, uid);
        return id;
    }

    private String media(long owner, String mediaId) {
        jdbc.update("INSERT INTO media_asset(media_id,client_upload_id,owner_account_id,purpose,mime_type,byte_size,width,height,frame_count,status,row_version,created_at,updated_at) VALUES(?,?,?,'PHOTO','image/jpeg',1024,2400,1600,1,'READY',0,NOW(6),NOW(6))",
                mediaId, UUID.randomUUID().toString(), owner);
        return mediaId;
    }

    private WorkRequests.Draft draft(String title, String mediaId) {
        return new WorkRequests.Draft(title, null, null, null, List.of(), List.of(mediaId));
    }

    private String sessionId(long accountId) {
        return jdbc.queryForObject("SELECT session_id FROM auth_session WHERE account_id=? ORDER BY id DESC LIMIT 1", String.class, accountId);
    }

    private void assertTagChanged(WorkViews.AuthorWork before, WorkViews.AuthorWork after) {
        assertThat(after.summary().versionTag()).isNotEqualTo(before.summary().versionTag());
    }

    private void assertError(ErrorCode expected, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(expected));
    }
}
