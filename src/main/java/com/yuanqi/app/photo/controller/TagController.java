package com.yuanqi.app.photo.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuanqi.app.common.api.ErrorCode;
import com.yuanqi.app.common.api.Result;
import com.yuanqi.app.common.exception.BusinessException;
import com.yuanqi.app.common.text.UnicodeText;
import com.yuanqi.app.photo.entity.PhotoTag;
import com.yuanqi.app.photo.mapper.PhotoTagMapper;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController @RequestMapping("/api/v1/tags")
public class TagController {
    private final PhotoTagMapper mapper; public TagController(PhotoTagMapper mapper){this.mapper=mapper;}
    @Operation(summary="按规范化前缀建议自由标签")
    @GetMapping public Result<List<TagView>> suggest(@RequestParam String q,@RequestParam(defaultValue="10") int limit){
        if(limit<1||limit>20)throw new BusinessException(ErrorCode.INVALID_PAGE_SIZE);
        String key=UnicodeText.comparisonKey(UnicodeText.trimUnicode(q));
        if(key==null||key.isEmpty())return Result.success(List.of());
        return Result.success(mapper.selectList(new LambdaQueryWrapper<PhotoTag>().likeRight(PhotoTag::getNormalizedName,key)
                .orderByAsc(PhotoTag::getNormalizedName).last("LIMIT "+limit)).stream().map(t->new TagView(t.getTagId(),t.getDisplayName())).toList());
    }
    public record TagView(String tagId,String name){}
}
