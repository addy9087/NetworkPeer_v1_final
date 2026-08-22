resource "aws_lb" "api" {
  name                       = local.alb_name
  internal                   = false
  load_balancer_type         = "application"
  security_groups            = [aws_security_group.alb.id]
  subnets                    = local.public_subnet_ids
  enable_deletion_protection = var.alb_deletion_protection
  enable_http2               = true
  idle_timeout               = var.alb_idle_timeout_seconds
  drop_invalid_header_fields = true

  lifecycle {
    precondition {
      condition     = !var.enable_http_redirect || var.enable_https_listener
      error_message = "enable_http_redirect requires enable_https_listener so port 80 never forwards application traffic."
    }

    precondition {
      condition = !var.enable_https_listener || (
        var.acm_certificate_arn != null || (var.domain_name != null && var.route53_zone_id != null)
      )
      error_message = "HTTPS requires an existing ACM certificate ARN or a domain_name with a Route53 zone Terraform can validate. For external DNS, validate ACM first and then provide acm_certificate_arn."
    }
  }
}

resource "aws_lb_target_group" "api" {
  name             = local.api_target_group_name
  port             = var.api_container_port
  protocol         = "HTTP"
  protocol_version = "HTTP1"
  target_type      = "ip"
  vpc_id           = aws_vpc.main.id

  deregistration_delay = 30

  health_check {
    enabled             = true
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 5
    matcher             = "200-399"
    path                = var.api_health_check_path
    protocol            = "HTTP"
  }
}

resource "aws_lb_listener" "https" {
  count = var.enable_https_listener ? 1 : 0

  load_balancer_arn = aws_lb.api.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = local.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }

  depends_on = [aws_acm_certificate_validation.application]
}

resource "aws_lb_listener" "http_redirect" {
  count = var.enable_http_redirect ? 1 : 0

  load_balancer_arn = aws_lb.api.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"

    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

resource "aws_acm_certificate" "application" {
  count = local.create_managed_certificate ? 1 : 0

  domain_name               = var.domain_name
  subject_alternative_names = tolist(var.additional_domain_names)
  validation_method         = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "acm_validation" {
  for_each = local.manage_acm_dns_validation ? {
    for option in aws_acm_certificate.application[0].domain_validation_options : option.domain_name => {
      name   = option.resource_record_name
      record = option.resource_record_value
      type   = option.resource_record_type
    }
  } : {}

  allow_overwrite = true
  name            = each.value.name
  records         = [each.value.record]
  ttl             = 60
  type            = each.value.type
  zone_id         = var.route53_zone_id
}

resource "aws_acm_certificate_validation" "application" {
  count = local.manage_acm_dns_validation ? 1 : 0

  certificate_arn         = aws_acm_certificate.application[0].arn
  validation_record_fqdns = [for record in aws_route53_record.acm_validation : record.fqdn]
}

resource "aws_route53_record" "application" {
  count = var.domain_name != null && var.route53_zone_id != null ? 1 : 0

  name    = var.domain_name
  type    = "A"
  zone_id = var.route53_zone_id

  alias {
    evaluate_target_health = true
    name                   = aws_lb.api.dns_name
    zone_id                = aws_lb.api.zone_id
  }
}

resource "aws_wafv2_web_acl" "application" {
  count = var.enable_waf ? 1 : 0

  name  = "${local.name_prefix}-api"
  scope = "REGIONAL"

  default_action {
    allow {}
  }

  rule {
    name     = "AWSManagedRulesCommonRuleSet"
    priority = 10

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "CommonRuleSet"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "AWSManagedRulesKnownBadInputsRuleSet"
    priority = 20

    override_action {
      none {}
    }

    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesKnownBadInputsRuleSet"
        vendor_name = "AWS"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "KnownBadInputs"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "RateLimit"
    priority = 30

    action {
      block {}
    }

    statement {
      rate_based_statement {
        aggregate_key_type = "IP"
        limit              = var.waf_rate_limit
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "RateLimit"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = replace(local.name_prefix, "-", "_")
    sampled_requests_enabled   = true
  }
}

resource "aws_wafv2_web_acl_association" "application" {
  count = var.enable_waf ? 1 : 0

  resource_arn = aws_lb.api.arn
  web_acl_arn  = aws_wafv2_web_acl.application[0].arn
}
