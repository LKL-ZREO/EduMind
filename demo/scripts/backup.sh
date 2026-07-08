#!/bin/sh
# PostgreSQL 自动备份脚本（每天凌晨 3 点由 pgbackup 容器执行）
# 保留最近 7 天的备份，自动清理过期文件

BACKUP_DIR="/backups"
DB_NAME="postgres"
RETENTION_DAYS=7

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="$BACKUP_DIR/${DB_NAME}_${TIMESTAMP}.sql.gz"

echo "[$(date)] 开始备份 $DB_NAME → $BACKUP_FILE"

pg_dump -h postgres -U "$PGUSER" "$DB_NAME" | gzip > "$BACKUP_FILE"

if [ $? -eq 0 ]; then
    echo "[$(date)] 备份成功 ($(du -h "$BACKUP_FILE" | cut -f1))"
else
    echo "[$(date)] 备份失败！"
    exit 1
fi

# 清理超过 ${RETENTION_DAYS} 天的旧备份
DELETED=$(find "$BACKUP_DIR" -name "${DB_NAME}_*.sql.gz" -mtime +${RETENTION_DAYS} -delete -print | wc -l)
if [ "$DELETED" -gt 0 ]; then
    echo "[$(date)] 已清理 $DELETED 个过期备份"
fi
