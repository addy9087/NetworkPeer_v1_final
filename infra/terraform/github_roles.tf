resource "aws_iam_role" "github_actions_plan" {
  name               = "${local.name_prefix}-github-plan"
  assume_role_policy = data.aws_iam_policy_document.github_actions_assume_role.json
}

resource "aws_iam_role" "github_actions_apply" {
  name               = "${local.name_prefix}-github-apply"
  assume_role_policy = data.aws_iam_policy_document.github_actions_assume_role.json
}

data "aws_iam_policy_document" "github_actions_plan" {
  statement {
    sid    = "ReadTerraformStateBucket"
    effect = "Allow"
    actions = [
      "s3:GetBucketLocation",
      "s3:ListBucket",
    ]
    resources = [local.terraform_state_bucket_arn]
  }

  statement {
    sid       = "ReadTerraformStateObjects"
    effect    = "Allow"
    actions   = ["s3:GetObject", "s3:GetObjectVersion"]
    resources = ["${local.terraform_state_bucket_arn}/*"]
  }

  statement {
    sid    = "LockTerraformState"
    effect = "Allow"
    actions = [
      "dynamodb:DeleteItem",
      "dynamodb:DescribeTable",
      "dynamodb:GetItem",
      "dynamodb:PutItem",
    ]
    resources = [local.terraform_lock_table_arn]
  }

  statement {
    sid    = "ReadEvidenceBucketConfiguration"
    effect = "Allow"
    actions = [
      "s3:GetBucketCORS",
      "s3:GetBucketAcl",
      "s3:GetEncryptionConfiguration",
      "s3:GetLifecycleConfiguration",
      "s3:GetBucketLocation",
      "s3:GetBucketOwnershipControls",
      "s3:GetBucketPolicy",
      "s3:GetBucketPolicyStatus",
      "s3:GetBucketPublicAccessBlock",
      "s3:GetBucketTagging",
      "s3:GetBucketVersioning",
      "s3:ListBucket",
    ]
    resources = [aws_s3_bucket.evidence.arn]
  }

  statement {
    sid    = "DescribeTargetInfrastructure"
    effect = "Allow"
    actions = [
      "acm:DescribeCertificate",
      "acm:ListCertificates",
      "acm:ListTagsForCertificate",
      "application-autoscaling:Describe*",
      "backup:Get*",
      "backup:List*",
      "cloudwatch:DescribeAlarms",
      "cloudwatch:GetDashboard",
      "cloudwatch:ListTagsForResource",
      "cognito-idp:Describe*",
      "cognito-idp:List*",
      "ec2:Describe*",
      "ecr:Describe*",
      "ecr:Get*",
      "ecr:List*",
      "ecs:Describe*",
      "ecs:List*",
      "ecs:ListTagsForResource",
      "elasticache:Describe*",
      "elasticache:List*",
      "elasticache:ListTagsForResource",
      "elasticloadbalancing:Describe*",
      "elasticloadbalancing:DescribeTags",
      "iam:Get*",
      "iam:List*",
      "logs:DescribeLogGroups",
      "logs:ListTagsForResource",
      "lambda:Get*",
      "lambda:List*",
      "rds:Describe*",
      "rds:ListTagsForResource",
      "route53:Get*",
      "route53:List*",
      "secretsmanager:DescribeSecret",
      "secretsmanager:ListSecrets",
      "secretsmanager:ListTagsForResource",
      "tag:GetResources",
      "wafv2:Get*",
      "wafv2:List*",
    ]
    resources = ["*"]
  }

  statement {
    sid       = "IdentifyCaller"
    effect    = "Allow"
    actions   = ["sts:GetCallerIdentity"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "github_actions_plan" {
  name   = "${local.name_prefix}-terraform-plan"
  role   = aws_iam_role.github_actions_plan.id
  policy = data.aws_iam_policy_document.github_actions_plan.json
}

data "aws_iam_policy_document" "github_actions_apply" {
  source_policy_documents = [data.aws_iam_policy_document.github_actions_plan.json]

  statement {
    sid       = "WriteTerraformStateObjects"
    effect    = "Allow"
    actions   = ["s3:PutObject"]
    resources = ["${local.terraform_state_bucket_arn}/*"]
  }

  statement {
    sid    = "ManageEvidenceBucket"
    effect = "Allow"
    actions = [
      "s3:CreateBucket",
      "s3:DeleteBucket",
      "s3:DeleteBucketPolicy",
      "s3:PutBucketCORS",
      "s3:PutEncryptionConfiguration",
      "s3:PutLifecycleConfiguration",
      "s3:PutBucketOwnershipControls",
      "s3:PutBucketPolicy",
      "s3:PutBucketPublicAccessBlock",
      "s3:PutBucketTagging",
      "s3:PutBucketVersioning",
    ]
    resources = [aws_s3_bucket.evidence.arn]
  }

  statement {
    sid    = "ManageCognitoCustomAuth"
    effect = "Allow"
    actions = [
      "cognito-idp:CreateGroup",
      "cognito-idp:CreateUserPool",
      "cognito-idp:CreateUserPoolClient",
      "cognito-idp:DeleteGroup",
      "cognito-idp:DeleteUserPool",
      "cognito-idp:DeleteUserPoolClient",
      "cognito-idp:SetUserPoolMfaConfig",
      "cognito-idp:TagResource",
      "cognito-idp:UntagResource",
      "cognito-idp:UpdateGroup",
      "cognito-idp:UpdateUserPool",
      "cognito-idp:UpdateUserPoolClient",
      "lambda:AddPermission",
      "lambda:CreateFunction",
      "lambda:DeleteFunction",
      "lambda:RemovePermission",
      "lambda:TagResource",
      "lambda:UntagResource",
      "lambda:UpdateFunctionCode",
      "lambda:UpdateFunctionConfiguration",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "ManageVpcAndSecurityGroups"
    effect = "Allow"
    actions = [
      "ec2:AllocateAddress",
      "ec2:AssociateRouteTable",
      "ec2:AttachInternetGateway",
      "ec2:AuthorizeSecurityGroupEgress",
      "ec2:AuthorizeSecurityGroupIngress",
      "ec2:CreateFlowLogs",
      "ec2:CreateInternetGateway",
      "ec2:CreateNatGateway",
      "ec2:CreateRoute",
      "ec2:CreateRouteTable",
      "ec2:CreateSecurityGroup",
      "ec2:CreateSubnet",
      "ec2:CreateTags",
      "ec2:CreateVpc",
      "ec2:CreateVpcEndpoint",
      "ec2:DeleteFlowLogs",
      "ec2:DeleteInternetGateway",
      "ec2:DeleteNatGateway",
      "ec2:DeleteRoute",
      "ec2:DeleteRouteTable",
      "ec2:DeleteSecurityGroup",
      "ec2:DeleteSubnet",
      "ec2:DeleteTags",
      "ec2:DeleteVpc",
      "ec2:DeleteVpcEndpoints",
      "ec2:DetachInternetGateway",
      "ec2:DisassociateRouteTable",
      "ec2:ModifySubnetAttribute",
      "ec2:ModifyVpcAttribute",
      "ec2:ModifyVpcEndpoint",
      "ec2:ReleaseAddress",
      "ec2:ReplaceRoute",
      "ec2:ReplaceRouteTableAssociation",
      "ec2:RevokeSecurityGroupEgress",
      "ec2:RevokeSecurityGroupIngress",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "ManageEcrRepositories"
    effect = "Allow"
    actions = [
      "ecr:CreateRepository",
      "ecr:DeleteLifecyclePolicy",
      "ecr:DeleteRepository",
      "ecr:PutImageScanningConfiguration",
      "ecr:PutLifecyclePolicy",
      "ecr:TagResource",
      "ecr:UntagResource",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "ManageEcsResources"
    effect = "Allow"
    actions = [
      "ecs:CreateCluster",
      "ecs:CreateService",
      "ecs:DeleteCluster",
      "ecs:DeleteService",
      "ecs:DeregisterTaskDefinition",
      "ecs:PutClusterCapacityProviders",
      "ecs:RegisterTaskDefinition",
      "ecs:RunTask",
      "ecs:StopTask",
      "ecs:TagResource",
      "ecs:UntagResource",
      "ecs:UpdateClusterSettings",
      "ecs:UpdateService",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "ManageLoadBalancing"
    effect = "Allow"
    actions = [
      "elasticloadbalancing:AddTags",
      "elasticloadbalancing:CreateListener",
      "elasticloadbalancing:CreateLoadBalancer",
      "elasticloadbalancing:CreateTargetGroup",
      "elasticloadbalancing:DeleteListener",
      "elasticloadbalancing:DeleteLoadBalancer",
      "elasticloadbalancing:DeleteTargetGroup",
      "elasticloadbalancing:ModifyLoadBalancerAttributes",
      "elasticloadbalancing:ModifyListener",
      "elasticloadbalancing:ModifyTargetGroup",
      "elasticloadbalancing:ModifyTargetGroupAttributes",
      "elasticloadbalancing:RemoveTags",
      "elasticloadbalancing:SetSecurityGroups",
      "elasticloadbalancing:SetSubnets",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "ManagePostgresAndRedis"
    effect = "Allow"
    actions = [
      "elasticache:AddTagsToResource",
      "elasticache:CreateReplicationGroup",
      "elasticache:CreateCacheSubnetGroup",
      "elasticache:DeleteCacheSubnetGroup",
      "elasticache:DeleteReplicationGroup",
      "elasticache:ModifyReplicationGroup",
      "elasticache:ModifyCacheSubnetGroup",
      "elasticache:RemoveTagsFromResource",
      "rds:AddTagsToResource",
      "rds:CreateDBInstance",
      "rds:CreateDBParameterGroup",
      "rds:CreateDBSubnetGroup",
      "rds:DeleteDBInstance",
      "rds:DeleteDBParameterGroup",
      "rds:DeleteDBSubnetGroup",
      "rds:ModifyDBInstance",
      "rds:ModifyDBParameterGroup",
      "rds:ModifyDBSubnetGroup",
      "rds:RebootDBInstance",
      "rds:RemoveTagsFromResource",
      "rds:ResetDBParameterGroup",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "ManageLogsAlarmsAndScaling"
    effect = "Allow"
    actions = [
      "application-autoscaling:DeleteScalingPolicy",
      "application-autoscaling:DeregisterScalableTarget",
      "application-autoscaling:PutScalingPolicy",
      "application-autoscaling:RegisterScalableTarget",
      "cloudwatch:DeleteAlarms",
      "cloudwatch:DeleteDashboards",
      "cloudwatch:PutDashboard",
      "cloudwatch:PutMetricAlarm",
      "cloudwatch:TagResource",
      "cloudwatch:UntagResource",
      "logs:CreateLogDelivery",
      "logs:CreateLogGroup",
      "logs:DeleteLogDelivery",
      "logs:DeleteLogGroup",
      "logs:GetLogDelivery",
      "logs:ListLogDeliveries",
      "logs:PutRetentionPolicy",
      "logs:TagResource",
      "logs:UntagResource",
      "logs:UpdateLogDelivery",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "ManageBackupPolicy"
    effect = "Allow"
    actions = [
      "backup:CreateBackupPlan",
      "backup:CreateBackupSelection",
      "backup:CreateBackupVault",
      "backup:DeleteBackupPlan",
      "backup:DeleteBackupSelection",
      "backup:DeleteBackupVault",
      "backup:DeleteBackupVaultLockConfiguration",
      "backup:DeleteRecoveryPoint",
      "backup:PutBackupVaultLockConfiguration",
      "backup:TagResource",
      "backup:UntagResource",
      "backup:UpdateBackupPlan",
      "backup:UpdateRecoveryPointLifecycle",
    ]
    resources = ["*"]
  }

  statement {
    sid    = "ManageAcmAndWaf"
    effect = "Allow"
    actions = [
      "acm:AddTagsToCertificate",
      "acm:DeleteCertificate",
      "acm:RemoveTagsFromCertificate",
      "acm:RequestCertificate",
      "wafv2:AssociateWebACL",
      "wafv2:CreateWebACL",
      "wafv2:DeleteWebACL",
      "wafv2:DisassociateWebACL",
      "wafv2:TagResource",
      "wafv2:UntagResource",
      "wafv2:UpdateWebACL",
    ]
    resources = ["*"]
  }

  dynamic "statement" {
    for_each = var.route53_zone_id == null ? [] : [var.route53_zone_id]

    content {
      sid    = "ManageConfiguredRoute53Zone"
      effect = "Allow"
      actions = [
        "route53:ChangeResourceRecordSets",
        "route53:GetHostedZone",
        "route53:ListResourceRecordSets",
      ]
      resources = ["arn:${data.aws_partition.current.partition}:route53:::hostedzone/${statement.value}"]
    }
  }

  statement {
    sid    = "ManageTerraformIamRoles"
    effect = "Allow"
    actions = [
      "iam:AttachRolePolicy",
      "iam:DeleteRole",
      "iam:DeleteRolePolicy",
      "iam:DetachRolePolicy",
      "iam:GetRole",
      "iam:GetRolePolicy",
      "iam:PutRolePolicy",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:UpdateAssumeRolePolicy",
    ]
    resources = [local.iam_role_arn_prefix]
  }

  statement {
    sid       = "CreateTerraformIamRoles"
    effect    = "Allow"
    actions   = ["iam:CreateRole"]
    resources = [local.iam_role_arn_prefix]
  }

  statement {
    sid       = "PassOnlyManagedServiceRoles"
    effect    = "Allow"
    actions   = ["iam:PassRole"]
    resources = [local.iam_role_arn_prefix]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values = [
        "backup.amazonaws.com",
        "ecs-tasks.amazonaws.com",
        "lambda.amazonaws.com",
        "monitoring.rds.amazonaws.com",
        "vpc-flow-logs.amazonaws.com",
      ]
    }
  }

  statement {
    sid    = "ManageGithubOidcProvider"
    effect = "Allow"
    actions = [
      "iam:AddClientIDToOpenIDConnectProvider",
      "iam:DeleteOpenIDConnectProvider",
      "iam:RemoveClientIDFromOpenIDConnectProvider",
      "iam:TagOpenIDConnectProvider",
      "iam:UntagOpenIDConnectProvider",
      "iam:UpdateOpenIDConnectProviderThumbprint",
    ]
    resources = [local.github_oidc_provider_expected_arn]
  }

  statement {
    sid       = "CreateGithubOidcProvider"
    effect    = "Allow"
    actions   = ["iam:CreateOpenIDConnectProvider"]
    resources = ["*"]
  }

  statement {
    sid       = "CreateRequiredServiceLinkedRoles"
    effect    = "Allow"
    actions   = ["iam:CreateServiceLinkedRole"]
    resources = ["arn:${data.aws_partition.current.partition}:iam::*:role/aws-service-role/*"]

    condition {
      test     = "StringEquals"
      variable = "iam:AWSServiceName"
      values = [
        "backup.amazonaws.com",
        "ecs.amazonaws.com",
        "ecs.application-autoscaling.amazonaws.com",
        "elasticache.amazonaws.com",
        "elasticloadbalancing.amazonaws.com",
        "rds.amazonaws.com",
      ]
    }
  }

  statement {
    sid    = "ManageSecretContainersOnly"
    effect = "Allow"
    actions = [
      "secretsmanager:CreateSecret",
      "secretsmanager:DeleteSecret",
      "secretsmanager:TagResource",
      "secretsmanager:UntagResource",
      "secretsmanager:UpdateSecret",
    ]
    resources = ["arn:${data.aws_partition.current.partition}:secretsmanager:${var.aws_region}:${data.aws_caller_identity.current.account_id}:secret:${local.name_prefix}/*"]
  }
}

resource "aws_iam_role_policy" "github_actions_apply" {
  name   = "${local.name_prefix}-terraform-apply"
  role   = aws_iam_role.github_actions_apply.id
  policy = data.aws_iam_policy_document.github_actions_apply.json
}
