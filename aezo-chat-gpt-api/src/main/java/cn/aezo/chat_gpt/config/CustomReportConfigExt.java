package cn.aezo.chat_gpt.config;

import cn.aezo.chat_gpt.service.DictService;
import cn.aezo.chat_gpt.service.UserService;
import cn.aezo.chat_gpt.util.MiscU;
import cn.aezo.chat_gpt.util.ValidU;
import cn.aezo.share.reporttable.common.config.AbstractReportConfigExt;
import cn.aezo.share.reporttable.common.config.ReportTableConfig;
import cn.aezo.share.reporttable.common.config.ReportTableConst;
import cn.aezo.share.reporttable.common.exception.ReportAuthException;
import cn.aezo.share.reporttable.common.utils.R;
import cn.aezo.share.reporttable.common.utils.spring.SpringU;
import cn.aezo.share.reporttable.core.service.ReportApiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CustomReportConfigExt extends AbstractReportConfigExt {
    @Value("${aezo-app-common.rtadmin.allowStaffWhenNeedPermissionNull:false}")
    private String allowStaffWhenNeedPermissionNull;

    @Value("${aezo-app-common.rtadmin.allowStaffWhenNodeNeedPermissionNull:true}")
    private String allowStaffWhenNodeNeedPermissionNull;

    @Override
    public String getUsername() {
        return DictService.getUsername();
    }

    @Override
    public String getName() {
        return DictService.getName();
    }

    @Override
    public String getUserId() {
        return DictService.getUserId();
    }

    @Override
    public String getSaasId() {
        return DictService.getSaasId();
    }

    @Override
    public List<String> getPermissions(String username, String userId) {
        String userIdTemp = userId != null ? userId : getUserId();
        if(userIdTemp != null) {
            return SpringU.getBean(UserService.class).getPermissionList(userIdTemp);
        } else {
            return (List<String>) ThreadLocalContext.getContext(ThreadLocalContext.Permissions);
        }
    }

    @Override
    public Boolean hasPermission(String resourceType, String reportCode, String subCode, Object needPermissionObj,
                                 Map<String, Object> context, boolean throwError) {
        Boolean hasPermission = super.hasPermission(resourceType, reportCode, subCode, needPermissionObj, context, throwError);
        if(hasPermission == null) {
            hasPermission = false;
        }
        if(hasPermission) {
            if(ReportTableConst.ResourceTypeRtMenu.equals(resourceType)) {
                Map<String, Object> reportConfiguration = (Map<String, Object>) context.get("reportConfiguration");
                String type = (String) context.get("_type");
                if("menu".equals(type)) {
                    //hasPermission = (reportConfiguration.get("reportGroup") + "").contains("#Prod#");
                }
            }
        }
        if(throwError && !hasPermission) {
            if(ValidU.isEmpty(getUserId())) {
                throw new ReportAuthException("令牌失效或尚未登录", "auth.login.fail");
            } else {
                throw new ReportAuthException("无资源权限", "auth.perm.fail");
            }
        } else {
            return hasPermission;
        }
    }

    /**
     * 只有needPermissions为NULL，且前面判断没有权限的时候才会进入到此方法
     */
    @Override
    public Boolean hasPermissionWhenNeedPermissionNull(String resourceType, String reportCode, String subCode,
                                                       List<String> needPermissions, Map<String, Object> context,
                                                       boolean throwError) {
        // app开头的一般定义为用户端访问的资源，默认只需要用户登录
        boolean yesAppCode = subCode != null && (subCode.startsWith("app") || subCode.startsWith("sqlNode_app"));
        if(yesAppCode && ValidU.isNotEmpty(getUserId())) {
            return true;
        }
        Map<String, Object> reportConfiguration = (Map<String, Object>) context.get("reportConfiguration");
        if(reportConfiguration != null) {
            // parentRouteShow=false 一般为子组件
            Boolean parentRouteShow = (Boolean) (((Map<String, Object>) reportConfiguration.get("extMapSafe")).get("parentRouteShow"));
            if(parentRouteShow != null && !parentRouteShow) {
                return true;
            }
        }

        //if("true".equals(allowStaffWhenNeedPermissionNull)
        //        || (ReportTableConst.ResourceTypeSqlNode.equals(resourceType) && "true".equals(allowStaffWhenNodeNeedPermissionNull))) {
        //    // 此时只要报表没有限制权限，且用户有一级菜单权限则可以显示此报表菜单(如果报表限制了权限则按报表的来)
        //    List<String> permissionList = (List<String>) context.get("permissionList");
        //    List<String> staffPermission = MiscU.Instance.toList("rt.Staff", "rt.Mgr", "rt.Admin", "rt.Super", "rt.Dev");
        //    if(ValidU.isNotEmpty(permissionList) && permissionList.stream().anyMatch(staffPermission::contains)) {
        //        return true;
        //    }
        //}
        //
        //return false;

        String userId = getUserId();
        if(ValidU.isEmpty(userId)) {
            return false;
        } else {
            return MiscU.Instance.toList("MGR1", "MGR2").contains(DictService.getUserLevel());
        }
    }

    @Override
    public List<Map<String, Object>> dictMap(Map<String, Object> param) {
        String dictName = param.get("dictName") + "";
        if(dictName.startsWith("node_cfg.public")) {
            R res = SpringU.getBean(ReportApiService.class).runSqlNode("biz_app_common", "appDict",
                    MiscU.Instance.toMap("parentCode", param.get("parent_code"), "paging", false));
            if(R.isSuccess(res) && res.getData() != null) {
                return (List<Map<String, Object>>) res.getData();
            } else {
                return new ArrayList<>();
            }
        } else if (dictName.startsWith("node_")) {
            List<String> permissions = getPermissions(null, null);
            List<String> staffPermission = MiscU.Instance.toList("rt.Staff", "rt.Super", "rt.Dev");
            if(!permissions.stream().anyMatch(x -> staffPermission.contains(x))) {
                // 无权访问
                return new ArrayList<>();
            }
        }
        return null;
    }

    @Override
    public Map<String, Object> getReportGroupDictMap() {
        return MiscU.Instance.toMapExt(true,
                "Prod", "生产", "Test", "测试", "Dev", "开发",
                "Page", "页面", "Comp", "组件",
                "Biz", "业务", "Fin", "财务", "Report", "报表", "Conf", "维护", "Mgr", "管理", "Default", "默认");
    }

    @Override
    public String getFileConfigBasePath() {
        return SpringU.getBean(ReportTableConfig.class).getBaseFolder();
    }
}
