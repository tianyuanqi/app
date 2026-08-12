package com.yuanqi.app.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yuanqi.app.auth.service.AuthService;
import com.yuanqi.app.auth.support.AuthPolicy;
import com.yuanqi.app.auth.support.ClientInfo;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.photo.enums.PhotoStatus;
import com.yuanqi.app.photo.mapper.PhotoInfoMapper;
import com.yuanqi.app.photo.entity.PhotoInfo;
import com.yuanqi.app.photo.service.PhotoService;
import com.yuanqi.app.photo.vo.PhotoCardVO;
import com.yuanqi.app.user.dto.UserRequests;
import com.yuanqi.app.user.entity.User;
import com.yuanqi.app.user.mapper.UserMapper;
import com.yuanqi.app.user.service.UserService;
import com.yuanqi.app.user.vo.UserProfileVO;
import com.yuanqi.app.user.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 用户资料服务实现。
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final PhotoService photoService;
    private final PhotoInfoMapper photoInfoMapper;

    public UserServiceImpl(UserMapper userMapper,
                           PasswordEncoder passwordEncoder,
                           AuthService authService,
                           PhotoService photoService,
                           PhotoInfoMapper photoInfoMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.photoService = photoService;
        this.photoInfoMapper = photoInfoMapper;
    }

    @Override
    public UserVO getProfile(Long userId) {
        return toUserVO(requireUser(userId));
    }

    @Override
    public UserVO updateProfile(Long userId, UserRequests.UpdateProfile request) {
        User user = requireUser(userId);
        String email = normalizeEmail(request.getEmail());
        if (email != null && userMapper.exists(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email).ne(User::getId, userId))) {
            throw new BusinessException(ErrorCode.AUTH_EMAIL_EXISTS);
        }
        if (email != null) {
            user.setEmail(email);
        }
        if (request.getBirth() != null) {
            user.setBirth(request.getBirth());
        }
        if (request.getGender() != null) {
            if (request.getGender() < 0 || request.getGender() > 2) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "性别参数只能为0、1或2");
            }
            user.setGender(request.getGender());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio().trim());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return toUserVO(user);
    }

    @Override
    public UserProfileVO getPublicProfile(String uid) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUid, uid));
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        long photoCount = photoInfoMapper.selectCount(new LambdaQueryWrapper<PhotoInfo>()
                .eq(PhotoInfo::getUserId, user.getId())
                .eq(PhotoInfo::getStatus, PhotoStatus.PUBLISHED.name()));
        UserProfileVO vo = new UserProfileVO();
        vo.setUid(user.getUid());
        vo.setUsername(user.getUsername());
        vo.setBio(user.getBio());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setPhotoCount(photoCount);
        vo.setJoinedAt(user.getCreatedAt());
        return vo;
    }

    @Override
    public IPage<PhotoCardVO> listPublicPhotos(String uid, Integer current, Integer pageSize) {
        return photoService.listPublishedByUid(uid, current, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void changePassword(Long userId, UserRequests.ChangePassword request) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "原密码错误");
        }
        AuthPolicy.validatePassword(request.getNewPassword());
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码不能与原密码相同");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        HttpServletRequest httpRequest = currentRequest();
        authService.revokeAllSessionsAfterPasswordChange(
                userId,
                ClientInfo.ip(httpRequest),
                ClientInfo.userAgent(httpRequest));
    }

    private User requireUser(Long userId) {
        User user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setUid(user.getUid());
        vo.setUsername(user.getUsername());
        vo.setBirth(user.getBirth());
        vo.setGender(user.getGender());
        vo.setEmail(user.getEmail());
        vo.setBio(user.getBio());
        vo.setAvatarUrl(user.getAvatarUrl());
        return vo;
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }
}
