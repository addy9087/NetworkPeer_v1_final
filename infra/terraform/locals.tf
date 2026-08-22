locals {
  name_prefix = "${var.project}-${var.environment}"
  github_oidc_provider_arn = coalesce(
    var.existing_github_oidc_provider_arn,
    try(aws_iam_openid_connect_provider.github_actions[0].arn, null),
  )
  common_tags = {
    Application      = "NetworkPeer"
    Environment      = var.environment
    ManagedBy        = "Terraform"
    Repository       = var.github_repository
    DeploymentBranch = var.github_branch
  }
}
