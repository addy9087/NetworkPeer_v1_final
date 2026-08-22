resource "aws_security_group" "alb" {
  name        = "${local.name_prefix}-alb"
  description = "Public ingress only for the NetworkPeer application load balancer"
  vpc_id      = aws_vpc.main.id

  dynamic "ingress" {
    for_each = var.enable_http_redirect ? [1] : []

    content {
      description = "HTTP is accepted only for the HTTPS redirect listener"
      from_port   = 80
      to_port     = 80
      protocol    = "tcp"
      cidr_blocks = ["0.0.0.0/0"]
    }
  }

  dynamic "ingress" {
    for_each = var.enable_https_listener ? [1] : []

    content {
      description = "Public HTTPS"
      from_port   = 443
      to_port     = 443
      protocol    = "tcp"
      cidr_blocks = ["0.0.0.0/0"]
    }
  }

  egress {
    description = "ALB may reach the private API port within this VPC only"
    from_port   = var.api_container_port
    to_port     = var.api_container_port
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }
}

resource "aws_security_group" "ecs_tasks" {
  name        = "${local.name_prefix}-ecs-tasks"
  description = "API, worker, and one-shot migration Fargate task traffic"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Only the ALB may call the Fastify API"
    from_port       = var.api_container_port
    to_port         = var.api_container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    description = "HTTPS for AWS endpoints and explicitly required third-party APIs through NAT"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "PostgreSQL only within the VPC; the database SG further restricts ingress"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  egress {
    description = "Redis only within the VPC; the cache SG further restricts ingress"
    from_port   = 6379
    to_port     = 6379
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }
}

resource "aws_security_group" "rds" {
  name        = "${local.name_prefix}-rds"
  description = "PostgreSQL accepts TLS traffic only from ECS task ENIs"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "ECS tasks to PostgreSQL"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_tasks.id]
  }

  egress = []
}

resource "aws_security_group" "redis" {
  name        = "${local.name_prefix}-redis"
  description = "Redis accepts TLS traffic only from ECS task ENIs"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "ECS tasks to Redis"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_tasks.id]
  }

  egress = []
}

resource "aws_security_group" "vpc_endpoints" {
  name        = "${local.name_prefix}-vpc-endpoints"
  description = "PrivateLink HTTPS ingress from ECS tasks"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "ECS tasks to interface endpoints"
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_tasks.id]
  }

  egress = []
}
