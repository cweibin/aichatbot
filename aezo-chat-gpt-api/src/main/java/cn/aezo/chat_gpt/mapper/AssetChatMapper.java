package cn.aezo.chat_gpt.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface AssetChatMapper {
    int updateUserAsset(@Param("userId") String userId, @Param("assetType") String assetType,
                        @Param("asset") BigDecimal asset, @Param("version") Integer version);
    int insertUserAssetHis(@Param("ctx") Map<String, Object> ctx);

    IPage<Map<String, Object>> findPromptList(Page page, @Param("ctx") Map<String, Object> params);

    List<Map<String, Object>> findPromptList(@Param("ctx") Map<String, Object> params);
}
