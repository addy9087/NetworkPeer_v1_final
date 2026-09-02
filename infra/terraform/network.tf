resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  lifecycle {
    precondition {
      condition     = length(local.selected_availability_zones) == 2
      error_message = "The target architecture requires exactly two available Availability Zones."
    }

    precondition {
      condition = var.nat_gateway_mode != "none" || (
        var.enable_interface_vpc_endpoints &&
        var.enable_s3_gateway_endpoint &&
        length(setsubtract(toset(["ecr.api", "ecr.dkr", "logs", "secretsmanager", "cognito-idp"]), var.interface_vpc_endpoint_services)) == 0
      )
      error_message = "nat_gateway_mode=none requires S3 plus ECR API/DKR, Logs, Secrets Manager, and Cognito IDP VPC endpoints so private Fargate tasks can start and authenticate users."
    }
  }
}

resource "aws_default_security_group" "main" {
  vpc_id = aws_vpc.main.id

  ingress = []
  egress  = []
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
}

resource "aws_subnet" "public" {
  for_each = local.subnet_configuration

  vpc_id                  = aws_vpc.main.id
  availability_zone       = each.key
  cidr_block              = each.value.public_cidr
  map_public_ip_on_launch = true

  tags = {
    Name = "${local.name_prefix}-public-${each.key}"
    Tier = "public"
  }
}

resource "aws_subnet" "private_app" {
  for_each = local.subnet_configuration

  vpc_id                  = aws_vpc.main.id
  availability_zone       = each.key
  cidr_block              = each.value.private_app_cidr
  map_public_ip_on_launch = false

  tags = {
    Name = "${local.name_prefix}-app-${each.key}"
    Tier = "private-app"
  }
}

resource "aws_subnet" "private_data" {
  for_each = local.subnet_configuration

  vpc_id                  = aws_vpc.main.id
  availability_zone       = each.key
  cidr_block              = each.value.private_data_cidr
  map_public_ip_on_launch = false

  tags = {
    Name = "${local.name_prefix}-data-${each.key}"
    Tier = "private-data"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-public"
    Tier = "public"
  }
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.main.id
}

resource "aws_route_table_association" "public" {
  for_each = local.subnet_configuration

  subnet_id      = aws_subnet.public[each.key].id
  route_table_id = aws_route_table.public.id
}

resource "aws_eip" "nat" {
  for_each = local.nat_gateway_azs

  domain = "vpc"

  tags = {
    Name = "${local.name_prefix}-nat-${each.key}"
  }
}

resource "aws_nat_gateway" "main" {
  for_each = local.nat_gateway_azs

  allocation_id = aws_eip.nat[each.key].id
  subnet_id     = aws_subnet.public[each.key].id

  depends_on = [aws_internet_gateway.main]

  tags = {
    Name = "${local.name_prefix}-nat-${each.key}"
  }
}

resource "aws_route_table" "private_app" {
  for_each = local.subnet_configuration

  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-app-${each.key}"
    Tier = "private-app"
  }
}

resource "aws_route" "private_app_nat" {
  for_each = var.nat_gateway_mode == "none" ? {} : local.subnet_configuration

  route_table_id         = aws_route_table.private_app[each.key].id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id = aws_nat_gateway.main[
    var.nat_gateway_mode == "single" ? local.first_availability_zone : each.key
  ].id
}

resource "aws_route_table_association" "private_app" {
  for_each = local.subnet_configuration

  subnet_id      = aws_subnet.private_app[each.key].id
  route_table_id = aws_route_table.private_app[each.key].id
}

resource "aws_route_table" "private_data" {
  for_each = local.subnet_configuration

  vpc_id = aws_vpc.main.id

  tags = {
    Name = "${local.name_prefix}-data-${each.key}"
    Tier = "private-data"
  }
}

resource "aws_route_table_association" "private_data" {
  for_each = local.subnet_configuration

  subnet_id      = aws_subnet.private_data[each.key].id
  route_table_id = aws_route_table.private_data[each.key].id
}

resource "aws_vpc_endpoint" "s3" {
  count = var.enable_s3_gateway_endpoint ? 1 : 0

  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids = [
    for availability_zone in local.selected_availability_zones : aws_route_table.private_app[availability_zone].id
  ]
}

resource "aws_vpc_endpoint" "interface" {
  for_each = var.enable_interface_vpc_endpoints ? var.interface_vpc_endpoint_services : toset([])

  vpc_id              = aws_vpc.main.id
  service_name        = "com.amazonaws.${var.aws_region}.${each.value}"
  vpc_endpoint_type   = "Interface"
  private_dns_enabled = true
  subnet_ids          = local.private_app_subnet_ids
  security_group_ids  = [aws_security_group.vpc_endpoints.id]

  tags = {
    Name = "${local.name_prefix}-${replace(each.value, ".", "-")}-endpoint"
  }
}

data "aws_iam_policy_document" "vpc_flow_logs_assume_role" {
  count = var.enable_vpc_flow_logs ? 1 : 0

  statement {
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["vpc-flow-logs.amazonaws.com"]
    }

    actions = ["sts:AssumeRole"]
  }
}

resource "aws_cloudwatch_log_group" "vpc_flow" {
  count = var.enable_vpc_flow_logs ? 1 : 0

  name              = "/aws/vpc/${local.name_prefix}/flow"
  retention_in_days = var.vpc_flow_log_retention_days
}

resource "aws_iam_role" "vpc_flow_logs" {
  count = var.enable_vpc_flow_logs ? 1 : 0

  name               = "${local.name_prefix}-vpc-flow-logs"
  assume_role_policy = data.aws_iam_policy_document.vpc_flow_logs_assume_role[0].json
}

data "aws_iam_policy_document" "vpc_flow_logs" {
  count = var.enable_vpc_flow_logs ? 1 : 0

  statement {
    effect = "Allow"
    actions = [
      "logs:CreateLogStream",
      "logs:DescribeLogStreams",
      "logs:PutLogEvents",
    ]
    resources = ["${aws_cloudwatch_log_group.vpc_flow[0].arn}:*"]
  }
}

resource "aws_iam_role_policy" "vpc_flow_logs" {
  count = var.enable_vpc_flow_logs ? 1 : 0

  name   = "${local.name_prefix}-vpc-flow-logs"
  role   = aws_iam_role.vpc_flow_logs[0].id
  policy = data.aws_iam_policy_document.vpc_flow_logs[0].json
}

resource "aws_flow_log" "vpc" {
  count = var.enable_vpc_flow_logs ? 1 : 0

  iam_role_arn             = aws_iam_role.vpc_flow_logs[0].arn
  log_destination          = aws_cloudwatch_log_group.vpc_flow[0].arn
  log_destination_type     = "cloud-watch-logs"
  max_aggregation_interval = 60
  traffic_type             = "ALL"
  vpc_id                   = aws_vpc.main.id
}
