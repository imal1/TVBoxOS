package com.github.tvbox.osc.util;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.orhanobut.hawk.Hawk;

import java.util.List;

/**
 * 外部存储访问统一走「所有文件访问权限」(MANAGE_EXTERNAL_STORAGE)。
 * targetSdk 30+ 下 READ/WRITE_EXTERNAL_STORAGE 已无法访问共享存储，
 * 获得该权限后既有的外部存储路径写法无需改动。
 */
public final class StoragePermission {

    public static final String PERMISSION = Permission.MANAGE_EXTERNAL_STORAGE;

    private static final String KEY_PROMPTED = "storage_permission_prompted";

    private StoragePermission() {
    }

    public static boolean isGranted(Context context) {
        return XXPermissions.isGranted(context, PERMISSION);
    }

    /**
     * 申请权限（会跳转到系统的「所有文件访问权限」设置页），授予后执行 onGranted。
     */
    public static void request(Activity activity, Runnable onGranted) {
        if (activity == null) return;
        if (isGranted(activity)) {
            if (onGranted != null) onGranted.run();
            return;
        }
        XXPermissions.with(activity)
                .permission(PERMISSION)
                .request(new OnPermissionCallback() {
                    @Override
                    public void onGranted(List<String> permissions, boolean all) {
                        if (!all) return;
                        Toast.makeText(activity, "已获得所有文件访问权限", Toast.LENGTH_SHORT).show();
                        if (onGranted != null) onGranted.run();
                    }

                    @Override
                    public void onDenied(List<String> permissions, boolean never) {
                        showDeniedTip(activity);
                    }
                });
    }

    /**
     * 首次启动时说明用途并引导授权；只提示一次，之后由具体功能按需申请。
     */
    public static void promptOnFirstLaunch(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (isGranted(activity) || Hawk.get(KEY_PROMPTED, false)) return;
        Hawk.put(KEY_PROMPTED, true);
        new AlertDialog.Builder(activity)
                .setTitle("需要「所有文件访问权限」")
                .setMessage("用于读取本地视频、导入导出配置、备份还原、加载本地 jar 以及接收局域网推送的文件。\n\n"
                        + "接下来会打开系统设置页，请为 TVBox 打开「允许访问所有文件」。")
                .setPositiveButton("去授权", (dialog, which) -> request(activity, null))
                .setNegativeButton("暂不", (dialog, which) -> showDeniedTip(activity))
                .setOnCancelListener(dialog -> showDeniedTip(activity))
                .show();
    }

    private static void showDeniedTip(Context context) {
        Toast.makeText(context, "未授予所有文件访问权限，本地文件、备份还原等功能暂不可用，可稍后在设置中重新授权",
                Toast.LENGTH_LONG).show();
    }
}
