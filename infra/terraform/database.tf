resource "aws_db_subnet_group" "postgres" {
  name       = "${local.name_prefix}-postgres"
  subnet_ids = local.private_data_subnet_ids

  tags = {
    Name = "${local.name_prefix}-postgres"
  }
}

resource "aws_db_parameter_group" "postgres" {
  name_prefix = "${local.name_prefix}-postgres-"
  family      = var.postgres_parameter_group_family
  description = "NetworkPeer PostgreSQL TLS and slow-query settings"

  parameter {
    name         = "rds.force_ssl"
    value        = "1"
    apply_method = "pending-reboot"
  }

  parameter {
    name         = "log_connections"
    value        = "1"
    apply_method = "immediate"
  }

  parameter {
    name         = "log_disconnections"
    value        = "1"
    apply_method = "immediate"
  }

  parameter {
    name         = "log_min_duration_statement"
    value        = "1000"
    apply_method = "immediate"
  }
}

data "aws_iam_policy_document" "rds_monitoring_assume_role" {
  count = var.rds_monitoring_interval_seconds > 0 ? 1 : 0

  statement {
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["monitoring.rds.amazonaws.com"]
    }

    actions = ["sts:AssumeRole"]
  }
}

resource "aws_iam_role" "rds_monitoring" {
  count = var.rds_monitoring_interval_seconds > 0 ? 1 : 0

  name               = "${local.name_prefix}-rds-monitoring"
  assume_role_policy = data.aws_iam_policy_document.rds_monitoring_assume_role[0].json
}

resource "aws_iam_role_policy_attachment" "rds_monitoring" {
  count = var.rds_monitoring_interval_seconds > 0 ? 1 : 0

  role       = aws_iam_role.rds_monitoring[0].name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

resource "aws_cloudwatch_log_group" "rds_postgresql" {
  name              = "/aws/rds/instance/${local.rds_identifier}/postgresql"
  retention_in_days = var.rds_log_retention_days
}

resource "aws_db_instance" "postgres" {
  identifier = local.rds_identifier

  engine         = "postgres"
  engine_version = var.postgres_engine_version
  instance_class = var.rds_instance_class

  db_name                     = var.rds_database_name
  username                    = var.rds_master_username
  manage_master_user_password = true
  port                        = 5432

  allocated_storage     = var.rds_allocated_storage_gb
  max_allocated_storage = var.rds_max_allocated_storage_gb
  storage_type          = "gp3"
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.postgres.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.postgres.name
  publicly_accessible    = false
  multi_az               = var.rds_multi_az

  backup_retention_period = var.rds_backup_retention_days
  backup_window           = var.rds_preferred_backup_window
  maintenance_window      = var.rds_preferred_maintenance_window
  copy_tags_to_snapshot   = true

  deletion_protection = var.rds_deletion_protection
  skip_final_snapshot = var.rds_skip_final_snapshot
  final_snapshot_identifier = var.rds_skip_final_snapshot ? null : coalesce(
    var.rds_final_snapshot_identifier,
    "${local.rds_identifier}-final",
  )

  auto_minor_version_upgrade            = false
  allow_major_version_upgrade           = false
  apply_immediately                     = var.rds_apply_immediately
  monitoring_interval                   = var.rds_monitoring_interval_seconds
  monitoring_role_arn                   = var.rds_monitoring_interval_seconds > 0 ? aws_iam_role.rds_monitoring[0].arn : null
  performance_insights_enabled          = var.rds_performance_insights_enabled
  performance_insights_retention_period = var.rds_performance_insights_enabled ? var.rds_performance_insights_retention_period : null
  enabled_cloudwatch_logs_exports       = ["postgresql"]

  depends_on = [
    aws_cloudwatch_log_group.rds_postgresql,
    aws_iam_role_policy_attachment.rds_monitoring,
  ]

  lifecycle {
    precondition {
      condition     = var.rds_max_allocated_storage_gb >= var.rds_allocated_storage_gb
      error_message = "rds_max_allocated_storage_gb must be greater than or equal to rds_allocated_storage_gb."
    }
  }
}
