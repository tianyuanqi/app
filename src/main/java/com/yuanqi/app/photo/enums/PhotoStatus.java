package com.yuanqi.app.photo.enums;

/**
 * 作品发布状态。
 * <p>公开发现流与公开个人主页仅展示 PUBLISHED。</p>
 */
public enum PhotoStatus {
    /** 草稿：仅作者可见，可继续编辑后提交 */
    DRAFT,
    /** 待审核：上传默认状态，公开不可见 */
    PENDING,
    /** 已发布：进入首页与公开主页 */
    PUBLISHED,
    /** 审核驳回：仅作者与管理员可见 */
    REJECTED,
    /** 下架：曾发布后被管理员下线 */
    OFFLINE
}
