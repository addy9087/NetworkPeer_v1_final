variable "aws_region" {
  description = "AWS region for this environment. Keep RDS, ECS, ElastiCache, and S3 regional resources aligned."
  type        = string
}

variable "environment" {
  description = "Deployment environment name, for example staging or production."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9-]{2,20}$", var.environment))
    error_message = "environment must use lowercase letters, digits, and hyphens."
  }
}

variable "project" {
  description = "Stable project identifier used in AWS names and tags."
  type        = string
  default     = "networkpeer"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,20}$", var.project))
    error_message = "project must start with a lowercase letter and use lowercase letters, digits, and hyphens."
  }
}

variable "evidence_bucket_name" {
  description = "Globally unique S3 bucket name for private, versioned evidence."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$", var.evidence_bucket_name))
    error_message = "evidence_bucket_name must be a valid lowercase S3 bucket name."
  }
}

variable "evidence_pending_expiration_days" {
  description = "Days before an unconfirmed current evidence object tagged networkpeer-evidence-state=pending expires. Confirmed current evidence is not matched."
  type        = number
  default     = 7

  validation {
    condition     = var.evidence_pending_expiration_days >= 1 && floor(var.evidence_pending_expiration_days) == var.evidence_pending_expiration_days
    error_message = "evidence_pending_expiration_days must be a whole number of at least one day."
  }
}

variable "web_cors_origins" {
  description = "Exact HTTPS Vercel/browser origins that need direct presigned S3 POST uploads. Native apps do not need S3 CORS."
  type        = list(string)

  validation {
    condition     = alltrue([for origin in var.web_cors_origins : can(regex("^https://[^/]+$", origin))])
    error_message = "Every web_cors_origin must be an exact HTTPS origin without a path or trailing slash."
  }
}

variable "github_repository" {
  description = "GitHub owner/repository allowed to assume the deployment role, for example addy9087/Networkpeer."
  type        = string

  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_repository))
    error_message = "github_repository must use owner/repository format."
  }
}

variable "github_branch" {
  description = "Protected deployment branch recorded on all resources. Configure the matching branch restriction in the GitHub Environment; AWS OIDC trusts the environment subject."
  type        = string
  default     = "main"
}

variable "existing_github_oidc_provider_arn" {
  description = "Optional account-wide GitHub Actions OIDC provider ARN. Set this when the AWS account already owns one instead of creating a duplicate."
  type        = string
  default     = null
  nullable    = true
}

variable "secret_recovery_window_days" {
  description = "Secrets Manager recovery window. Use seven days or more outside disposable sandbox accounts."
  type        = number
  default     = 7

  validation {
    condition     = var.secret_recovery_window_days == 0 || (var.secret_recovery_window_days >= 7 && var.secret_recovery_window_days <= 30)
    error_message = "secret_recovery_window_days must be zero for a disposable sandbox, or between 7 and 30."
  }
}
