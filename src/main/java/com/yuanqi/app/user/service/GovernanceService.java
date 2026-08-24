package com.yuanqi.app.user.service;

import com.yuanqi.app.auth.entity.Account;import com.yuanqi.app.auth.entity.LoginSecurityState;
import com.yuanqi.app.auth.mapper.AccountMapper;import com.yuanqi.app.auth.mapper.LoginSecurityStateMapper;
import com.yuanqi.app.auth.service.AuthSessionService;import com.yuanqi.app.auth.support.AuthPolicy;import com.yuanqi.app.auth.support.PublicIdGenerator;
import com.yuanqi.app.common.api.ErrorCode;import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.user.entity.AccountGovernanceEvent;import com.yuanqi.app.user.entity.UserProfile;
import com.yuanqi.app.user.mapper.AccountGovernanceEventMapper;import com.yuanqi.app.user.mapper.UserProfileMapper;import com.yuanqi.app.user.vo.GovernanceViews;
import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;import java.time.LocalDateTime;import java.time.ZoneOffset;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@Service public class GovernanceService{
 private final AccountMapper accounts;private final UserProfileMapper profiles;private final LoginSecurityStateMapper security;
 private final AccountGovernanceEventMapper events;private final AuthSessionService sessions;private final PublicIdGenerator ids;private final Clock clock;
 public GovernanceService(AccountMapper a,UserProfileMapper p,LoginSecurityStateMapper s,AccountGovernanceEventMapper e,AuthSessionService ss,PublicIdGenerator i,Clock c){accounts=a;profiles=p;security=s;events=e;sessions=ss;ids=i;clock=c;}
 @Transactional public GovernanceViews.AccountSummary change(Long actor,String uid,String ifMatch,String reason,boolean disable){
  Account target=accounts.findByUidForUpdate(uid);if(target==null)throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
  if(!"USER".equals(target.getRole()))throw new BusinessException(ErrorCode.TARGET_NOT_GOVERNABLE);
  match(ifMatch,target);String desired=disable?"DISABLED":"ACTIVE";if(desired.equals(target.getGovernanceStatus()))throw new BusinessException(ErrorCode.STATE_CONFLICT);
  String before=target.getGovernanceStatus();target.setGovernanceStatus(desired);target.setRowVersion(target.getRowVersion()+1);target.setUpdatedAt(now());accounts.updateById(target);
  AccountGovernanceEvent event=new AccountGovernanceEvent();event.setPublicId(ids.next());event.setTargetAccountId(target.getId());event.setActorAccountId(actor);
  event.setAction(disable?"DISABLE":"ENABLE");event.setReason(AuthPolicy.validateReason(reason));event.setPreviousStatus(before);event.setResultingStatus(desired);event.setOccurredAt(now());events.insert(event);
  if(disable)sessions.revokeAll(target.getId(),"ACCOUNT_DISABLED");return view(target);
 }
 @Transactional(readOnly=true) public GovernanceViews.AccountPage list(String uid,String status,int page,int size,String sort){
  if(page<1)throw new BusinessException(ErrorCode.INVALID_PAGE);if(size<1||size>100)throw new BusinessException(ErrorCode.INVALID_PAGE_SIZE);
  if(uid!=null&&(uid.isEmpty()||uid.length()>64))throw new BusinessException(ErrorCode.INVALID_FILTER);
  if(status!=null&&!java.util.Set.of("ACTIVE","DISABLED").contains(status))throw new BusinessException(ErrorCode.INVALID_FILTER);
  if(!java.util.Set.of("JOINED_AT_DESC","JOINED_AT_ASC","UID_ASC").contains(sort))throw new BusinessException(ErrorCode.INVALID_SORT);
  LambdaQueryWrapper<Account> q=new LambdaQueryWrapper<Account>().eq(Account::getRole,"USER");if(uid!=null)q.eq(Account::getUid,uid);if(status!=null)q.eq(Account::getGovernanceStatus,status);
  if("JOINED_AT_DESC".equals(sort))q.orderByDesc(Account::getCreatedAt).orderByDesc(Account::getUid);else if("JOINED_AT_ASC".equals(sort))q.orderByAsc(Account::getCreatedAt).orderByAsc(Account::getUid);else q.orderByAsc(Account::getUid);
  Page<Account> result=accounts.selectPage(new Page<>(page,size),q);return new GovernanceViews.AccountPage(result.getRecords().stream().map(this::view).toList(),page,size,result.getTotal(),(int)result.getPages(),page>1,page<result.getPages(),new GovernanceViews.Filter(uid,status,sort));
 }
 @Transactional(readOnly=true) public com.yuanqi.app.common.api.PageResult<GovernanceViews.Event> history(String uid,int page,int size){if(page<1)throw new BusinessException(ErrorCode.INVALID_PAGE);if(size<1||size>100)throw new BusinessException(ErrorCode.INVALID_PAGE_SIZE);Account target=accounts.findByUid(uid);if(target==null||!"USER".equals(target.getRole()))throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);var q=new LambdaQueryWrapper<AccountGovernanceEvent>().eq(AccountGovernanceEvent::getTargetAccountId,target.getId()).orderByDesc(AccountGovernanceEvent::getOccurredAt).orderByDesc(AccountGovernanceEvent::getPublicId);Page<AccountGovernanceEvent> r=events.selectPage(new Page<>(page,size),q);var views=r.getRecords().stream().map(e->new GovernanceViews.Event(e.getPublicId(),e.getAction(),e.getReason(),accounts.selectById(e.getActorAccountId()).getUid(),e.getOccurredAt().atOffset(ZoneOffset.UTC))).toList();return com.yuanqi.app.common.api.PageResult.of(views,page,size,r.getTotal());}
 public GovernanceViews.AccountSummary view(Account a){UserProfile p=profiles.selectById(a.getId());LoginSecurityState s=security.selectById(a.getId());return new GovernanceViews.AccountSummary(a.getUid(),p.getUsername(),null,"USER",a.getGovernanceStatus(),s==null||s.getLockedUntil()==null?null:s.getLockedUntil().atOffset(ZoneOffset.UTC),a.getCreatedAt().atOffset(ZoneOffset.UTC),tag(a));}
 private void match(String v,Account a){if(v==null)throw new BusinessException(ErrorCode.PRECONDITION_REQUIRED);if(!tag(a).equals(v))throw new BusinessException(ErrorCode.PRECONDITION_FAILED);} private String tag(Account a){return "\"account-"+a.getRowVersion()+"\"";}private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);}
}
