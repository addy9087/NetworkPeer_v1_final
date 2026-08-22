resource "aws_elasticache_subnet_group" "redis" {
  name       = "${local.name_prefix}-redis"
  subnet_ids = local.private_data_subnet_ids

  tags = {
    Name = "${local.name_prefix}-redis"
  }
}

resource "aws_cloudwatch_log_group" "redis_engine" {
  name              = "/aws/elasticache/${local.name_prefix}/engine"
  retention_in_days = var.redis_log_retention_days
}

resource "aws_cloudwatch_log_group" "redis_slow" {
  name              = "/aws/elasticache/${local.name_prefix}/slow"
  retention_in_days = var.redis_log_retention_days
}

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id = local.redis_replication_name
  description          = "NetworkPeer Redis cache and queue backend"

  engine               = "redis"
  engine_version       = var.redis_engine_version
  node_type            = var.redis_node_type
  parameter_group_name = var.redis_parameter_group_name
  port                 = 6379

  num_cache_clusters         = var.redis_num_cache_clusters
  automatic_failover_enabled = var.redis_automatic_failover_enabled
  multi_az_enabled           = var.redis_multi_az_enabled
  preferred_cache_cluster_azs = var.redis_num_cache_clusters >= 2 ? local.selected_availability_zones : [
    local.first_availability_zone,
  ]
  subnet_group_name          = aws_elasticache_subnet_group.redis.name
  security_group_ids         = [aws_security_group.redis.id]
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  transit_encryption_mode    = "required"
  auth_token                 = var.redis_auth_token
  auth_token_update_strategy = "SET"
  auto_minor_version_upgrade = false
  apply_immediately          = var.redis_apply_immediately
  maintenance_window         = var.redis_maintenance_window
  snapshot_window            = var.redis_snapshot_window
  snapshot_retention_limit   = var.redis_snapshot_retention_limit

  log_delivery_configuration {
    destination      = aws_cloudwatch_log_group.redis_engine.name
    destination_type = "cloudwatch-logs"
    log_format       = "json"
    log_type         = "engine-log"
  }

  log_delivery_configuration {
    destination      = aws_cloudwatch_log_group.redis_slow.name
    destination_type = "cloudwatch-logs"
    log_format       = "json"
    log_type         = "slow-log"
  }

  lifecycle {
    precondition {
      condition     = var.redis_num_cache_clusters >= 1
      error_message = "redis_num_cache_clusters must be at least one."
    }

    precondition {
      condition     = !var.redis_automatic_failover_enabled || var.redis_num_cache_clusters >= 2
      error_message = "Redis automatic failover requires at least two cache nodes."
    }

    precondition {
      condition = !var.redis_multi_az_enabled || (
        var.redis_automatic_failover_enabled && var.redis_num_cache_clusters >= 2
      )
      error_message = "Redis Multi-AZ requires automatic failover and at least two cache nodes."
    }
  }
}
