package com.yuanqi.app.user.vo;
import java.time.OffsetDateTime;
public final class GovernanceViews{private GovernanceViews(){} public record AccountSummary(String uid,String username,Object avatar,String role,String governanceStatus,OffsetDateTime lockedUntil,OffsetDateTime joinedAt,String versionTag){} public record Filter(String uid,String governanceStatus,String sort){} public record AccountPage(java.util.List<AccountSummary> items,int page,int pageSize,long totalItems,int totalPages,boolean hasPrevious,boolean hasNext,Filter appliedFilter){} }
