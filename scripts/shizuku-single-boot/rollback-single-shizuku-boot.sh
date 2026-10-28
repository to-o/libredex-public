#!/system/bin/sh
set -e
rm -f /data/adb/service.d/20-shizuku-root.sh
pm default-state --user 0 moe.shizuku.privileged.api/moe.shizuku.manager.receiver.BootCompleteReceiver
sleep 15
echo 'Default Shizuku boot receiver restored; the running server is unchanged.'