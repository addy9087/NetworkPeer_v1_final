# AWS and Infra Status

This document summarizes required environment variables and service checks.

## Required env vars
- `AWS_REGION`
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`

## How to run checks
- `npx ts-node scripts/test-aws-services.ts`

If checks fail, ensure IAM user has `s3:ListAllMyBuckets` and minimal S3 permissions. Do not commit credentials.
