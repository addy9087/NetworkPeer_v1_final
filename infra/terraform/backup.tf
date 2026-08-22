resource "aws_backup_vault" "rds" {
  name = local.backup_vault_name
}

resource "aws_backup_plan" "rds" {
  name = "${local.name_prefix}-rds-daily"

  rule {
    rule_name         = "daily-rds-recovery-point"
    target_vault_name = aws_backup_vault.rds.name
    schedule          = var.backup_schedule

    lifecycle {
      delete_after = var.backup_delete_after_days
    }

    recovery_point_tags = merge(local.common_tags, {
      BackupPolicy = "daily-rds"
    })
  }
}

data "aws_iam_policy_document" "backup_assume_role" {
  statement {
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["backup.amazonaws.com"]
    }

    actions = ["sts:AssumeRole"]
  }
}

resource "aws_iam_role" "backup" {
  name               = "${local.name_prefix}-backup"
  assume_role_policy = data.aws_iam_policy_document.backup_assume_role.json
}

resource "aws_iam_role_policy_attachment" "backup" {
  role       = aws_iam_role.backup.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/service-role/AWSBackupServiceRolePolicyForBackup"
}

resource "aws_backup_selection" "rds" {
  iam_role_arn = aws_iam_role.backup.arn
  name         = "${local.name_prefix}-postgres"
  plan_id      = aws_backup_plan.rds.id
  resources    = [aws_db_instance.postgres.arn]

  depends_on = [aws_iam_role_policy_attachment.backup]
}

resource "aws_backup_vault_lock_configuration" "rds" {
  count = var.backup_vault_lock_min_retention_days == null ? 0 : 1

  backup_vault_name   = aws_backup_vault.rds.name
  min_retention_days  = var.backup_vault_lock_min_retention_days
  max_retention_days  = var.backup_vault_lock_max_retention_days
  changeable_for_days = var.backup_vault_lock_changeable_for_days

  lifecycle {
    precondition {
      condition     = var.backup_vault_lock_min_retention_days >= 1
      error_message = "backup_vault_lock_min_retention_days must be at least one when Vault Lock is enabled."
    }

    precondition {
      condition = var.backup_vault_lock_max_retention_days == null || (
        var.backup_vault_lock_max_retention_days >= var.backup_vault_lock_min_retention_days
      )
      error_message = "backup_vault_lock_max_retention_days must be null or at least the configured minimum retention."
    }
  }
}
