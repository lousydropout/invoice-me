import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecs from 'aws-cdk-lib/aws-ecs';
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2';
import * as rds from 'aws-cdk-lib/aws-rds';
import * as logs from 'aws-cdk-lib/aws-logs';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as acm from 'aws-cdk-lib/aws-certificatemanager';
import { Construct } from 'constructs';

/**
 * NOTE: This stack is configured for development/testing only.
 * It uses the default VPC (all-public subnets) with public IPs for ECS tasks.
 * For production, replace with private subnets, NAT gateways, and non-public RDS.
 */

/**
 * InvoiceMeStack
 * 
 * Complete infrastructure stack for InvoiceMe API deployment:
 * - Uses default VPC (all subnets are public)
 * - Aurora Serverless PostgreSQL database in public subnet (not publicly accessible)
 * - ECS Fargate cluster and service in public subnets
 * - Application Load Balancer for public access
 * - Security groups for secure communication
 */
export class InvoiceMeStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // Get domain name from context or environment variable, with fallback
    const domainName = this.node.tryGetContext('domainName') 
      || process.env.DOMAIN_NAME 
      || 'invoice-me.vincentchan.cloud';

    // ============================================================================
    // VPC & Networking
    // ============================================================================
    
    /**
     * Use the default VPC
     * Default VPC has public subnets in all availability zones
     * No NAT Gateways needed - resources use public IPs for internet access
     * Note: This requires the stack to have environment (account/region) set
     */
    const vpc = ec2.Vpc.fromLookup(this, 'DefaultVpc', {
      isDefault: true,
    });

    // ============================================================================
    // Security Groups
    // ============================================================================

    /**
     * Security group for the Application Load Balancer
     * Allows inbound HTTP traffic from the internet
     */
    const albSecurityGroup = new ec2.SecurityGroup(this, 'AlbSecurityGroup', {
      vpc,
      description: 'Security group for InvoiceMe ALB',
      allowAllOutbound: true,
    });
    albSecurityGroup.addIngressRule(
      ec2.Peer.anyIpv4(),
      ec2.Port.tcp(80),
      'Allow HTTP from internet'
    );
    albSecurityGroup.addIngressRule(
      ec2.Peer.anyIpv4(),
      ec2.Port.tcp(443),
      'Allow HTTPS from internet'
    );

    /**
     * Security group for ECS tasks
     * Allows inbound traffic on port 8080 from the ALB only
     */
    const ecsSecurityGroup = new ec2.SecurityGroup(this, 'EcsSecurityGroup', {
      vpc,
      description: 'Security group for InvoiceMe ECS tasks',
      allowAllOutbound: true,
    });
    ecsSecurityGroup.addIngressRule(
      albSecurityGroup,
      ec2.Port.tcp(8080),
      'Allow traffic from ALB'
    );

    /**
     * Security group for Aurora Serverless PostgreSQL
     * Allows inbound PostgreSQL traffic (port 5432) from ECS tasks only
     * allowAllOutbound: true enables outbound connections for monitoring/metrics
     */
    const rdsSecurityGroup = new ec2.SecurityGroup(this, 'RdsSecurityGroup', {
      vpc,
      description: 'Security group for InvoiceMe Aurora Serverless PostgreSQL',
      allowAllOutbound: true,
    });
    rdsSecurityGroup.addIngressRule(
      ecsSecurityGroup,
      ec2.Port.tcp(5432),
      'Allow PostgreSQL from ECS tasks'
    );
    rdsSecurityGroup.addIngressRule(
      ec2.Peer.anyIpv4(),
      ec2.Port.tcp(5432),
      'Allow PostgreSQL from any IPv4 address'
    );

    // ============================================================================
    // Aurora Serverless PostgreSQL Database
    // ============================================================================

    /**
     * Aurora Serverless v2 PostgreSQL cluster
     * 
     * DEVELOPMENT CONFIGURATION:
     * - Deployed in public subnets of default VPC (not publicly accessible)
     * - Serverless v2 auto-scales based on workload (min 0.5 ACU, max 1 ACU for dev)
     * - Aurora automatically distributes across multiple AZs for high availability
     * - Security groups restrict access to ECS tasks only
     * 
     * PRODUCTION: Use private subnets with NAT gateways for better security isolation
     * 
     * Benefits of Aurora Serverless v2:
     * - Auto-scaling: scales up/down based on workload
     * - Cost-effective: pay only for what you use
     * - PostgreSQL compatible: works with existing PostgreSQL applications
     * - High availability: built-in replication and failover
     * 
     * Other settings:
     * - Credentials stored in AWS Secrets Manager
     * - Storage encrypted at rest
     * - 7 days backup retention
     */
    const database = new rds.DatabaseCluster(this, 'InvoiceMeDatabase', {
      engine: rds.DatabaseClusterEngine.auroraPostgres({
        version: rds.AuroraPostgresEngineVersion.VER_17_4,
      }),
      writer: rds.ClusterInstance.serverlessV2('writer', {
        // Serverless v2 scaling configuration
        // ACU (Aurora Capacity Units): 0.5 ACU = ~1GB RAM, 1 vCPU
        scaleWithWriter: true,
      }),
      serverlessV2MinCapacity: 0.5, // Minimum capacity (0.5 ACU = ~1GB RAM)
      serverlessV2MaxCapacity: 1,    // Maximum capacity (1 ACU = ~2GB RAM) for development
      vpc,
      vpcSubnets: {
        subnetType: ec2.SubnetType.PUBLIC,
      },
      securityGroups: [rdsSecurityGroup],
      defaultDatabaseName: 'invoiceme',
      credentials: rds.Credentials.fromGeneratedSecret('invoiceme', {
        secretName: 'invoiceme/rds/credentials',
      }),
      removalPolicy: cdk.RemovalPolicy.DESTROY, // Change to RETAIN for production
      deletionProtection: false, // Set to true for production
      storageEncrypted: true,
      backup: {
        retention: cdk.Duration.days(7),
      },
    });

    // Enable Data API for Aurora Serverless v2
    // Note: enableDataApi is not available on DatabaseCluster L2 construct,
    // so we use the L1 construct to enable it
    const cfnCluster = database.node.defaultChild as rds.CfnDBCluster;
    cfnCluster.enableHttpEndpoint = true;

    // ============================================================================
    // ECS Cluster
    // ============================================================================

    /**
     * ECS Cluster with Fargate capacity providers
     * Fargate allows running containers without managing servers
     */
    const cluster = new ecs.Cluster(this, 'InvoiceMeCluster', {
      vpc,
      clusterName: 'invoiceme-cluster',
      enableFargateCapacityProviders: true,
    });

    // ============================================================================
    // CloudWatch Log Group
    // ============================================================================

    /**
     * CloudWatch Log Group for ECS container logs
     * Retention set to ONE_WEEK for development cost savings
     * For production, consider ONE_MONTH or LONGER for compliance/auditing
     */
    const logGroup = new logs.LogGroup(this, 'InvoiceMeLogGroup', {
      logGroupName: '/ecs/invoiceme-api',
      retention: logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    });

    // ============================================================================
    // ECS Task Definition
    // ============================================================================

    /**
     * Fargate Task Definition
     * Defines the container image, resources, and environment variables
     */
    const taskDefinition = new ecs.FargateTaskDefinition(this, 'InvoiceMeTask', {
      memoryLimitMiB: 512,
      cpu: 256,
    });

    // Get the Aurora secret to reference credentials
    const dbSecret = database.secret!;

    /**
     * ECR Repository Reference
     * References the existing ECR repository for the container image
     */
    const ecrRepositoryName = process.env.ECR_REPOSITORY_NAME || 'vincent-chan/invoice-me';
    const ecrRepository = ecr.Repository.fromRepositoryName(
      this,
      'InvoiceMeEcrRepository',
      ecrRepositoryName
    );

    /**
     * Container definition
     * Uses the ECR image and configures environment variables from Aurora secret
     * fromEcrRepository() automatically grants necessary ECR permissions
     */
    const imageTag = process.env.ECR_IMAGE_TAG || 'latest';
    const container = taskDefinition.addContainer('InvoiceMeContainer', {
      image: ecs.ContainerImage.fromEcrRepository(ecrRepository, imageTag),
      logging: ecs.LogDrivers.awsLogs({
        streamPrefix: 'invoiceme',
        logGroup: logGroup,
      }),
      environment: {
        DB_HOST: database.clusterEndpoint.hostname, // Aurora cluster endpoint
        DB_PORT: '5432',
        DB_NAME: 'invoiceme',
        SPRING_PROFILES_ACTIVE: 'prod',
        DOMAIN_NAME: domainName, // Domain name for OpenAPI server URL configuration
        // BasicAuth credentials from environment variables (if not using Secrets Manager)
        ...(process.env.SPRING_SECURITY_USER_NAME && {
          SPRING_SECURITY_USER_NAME: process.env.SPRING_SECURITY_USER_NAME,
        }),
        ...(process.env.SPRING_SECURITY_USER_PASSWORD && {
          SPRING_SECURITY_USER_PASSWORD: process.env.SPRING_SECURITY_USER_PASSWORD,
        }),
      },
      secrets: {
        DB_USER: ecs.Secret.fromSecretsManager(dbSecret, 'username'),
        DB_PASSWORD: ecs.Secret.fromSecretsManager(dbSecret, 'password'),
        // Optional: Use Secrets Manager for BasicAuth credentials instead of environment variables
        // Uncomment and configure if you prefer Secrets Manager:
        // SPRING_SECURITY_USER_NAME: ecs.Secret.fromSecretsManager(authSecret, 'username'),
        // SPRING_SECURITY_USER_PASSWORD: ecs.Secret.fromSecretsManager(authSecret, 'password'),
      },
    });

    container.addPortMappings({
      containerPort: 8080,
      protocol: ecs.Protocol.TCP,
    });

    // ============================================================================
    // Application Load Balancer
    // ============================================================================

    /**
     * Reference the existing ACM certificate for HTTPS
     * Certificate ARN is read from environment variable
     */
    const certificateArn = process.env.ACM_CERTIFICATE_ARN;
    if (!certificateArn) {
      throw new Error('ACM_CERTIFICATE_ARN environment variable is required. Please set it in your .env file.');
    }
    const certificate = acm.Certificate.fromCertificateArn(
      this,
      'InvoiceMeCert',
      certificateArn
    );

    /**
     * Internet-facing Application Load Balancer
     * Deployed in public subnets to accept traffic from the internet
     */
    const loadBalancer = new elbv2.ApplicationLoadBalancer(this, 'InvoiceMeAlb', {
      vpc,
      internetFacing: true,
      securityGroup: albSecurityGroup,
      vpcSubnets: {
        subnetType: ec2.SubnetType.PUBLIC,
      },
    });

    /**
     * Target Group for ECS service
     * Health check configured to use /api/health endpoint
     */
    const targetGroup = new elbv2.ApplicationTargetGroup(this, 'InvoiceMeTargetGroup', {
      vpc,
      port: 8080,
      protocol: elbv2.ApplicationProtocol.HTTP,
      targetType: elbv2.TargetType.IP,
      healthCheck: {
        path: '/api/health',
        interval: cdk.Duration.seconds(30),
        timeout: cdk.Duration.seconds(5),
        healthyThresholdCount: 2,
        unhealthyThresholdCount: 3,
      },
    });

    /**
     * HTTP Listener on port 80
     * Redirects all HTTP traffic to HTTPS
     */
    loadBalancer.addListener('InvoiceMeListener', {
      port: 80,
      protocol: elbv2.ApplicationProtocol.HTTP,
      defaultAction: elbv2.ListenerAction.redirect({
        protocol: 'HTTPS',
        port: '443',
        permanent: true,
      }),
    });

    /**
     * HTTPS Listener on port 443
     * Forwards traffic to the ECS service target group
     */
    loadBalancer.addListener('HttpsListener', {
      port: 443,
      protocol: elbv2.ApplicationProtocol.HTTPS,
      certificates: [certificate],
      defaultTargetGroups: [targetGroup],
    });

    // ============================================================================
    // ECS Service
    // ============================================================================

    /**
     * ECS Fargate Service
     * Runs the task definition and connects to the ALB target group
     * 
     * Network configuration:
     * - Deployed in public subnets (default VPC has no private subnets)
     * - assignPublicIp: true is required because there's no NAT gateway in default VPC
     *   This allows tasks to pull images from ECR and access other AWS services
     * 
     * Deployment configuration:
     * - minHealthyPercent: 100 ensures no downtime during updates
     * - maxHealthyPercent: 200 allows new tasks to start before old ones terminate
     * - platformVersion: LATEST uses the latest Fargate platform capabilities
     */
    const service = new ecs.FargateService(this, 'InvoiceMeService', {
      cluster,
      taskDefinition,
      desiredCount: 1,
      platformVersion: ecs.FargatePlatformVersion.LATEST,
      minHealthyPercent: 100,
      maxHealthyPercent: 200,
      securityGroups: [ecsSecurityGroup],
      vpcSubnets: {
        subnetType: ec2.SubnetType.PUBLIC,
      },
      assignPublicIp: true, // Required: no NAT gateway in default VPC
      healthCheckGracePeriod: cdk.Duration.seconds(60),
    });

    // Attach the service to the ALB target group
    service.attachToApplicationTargetGroup(targetGroup);

    // ============================================================================
    // Stack Outputs
    // ============================================================================

    /**
     * Output the ALB DNS name for easy access to the API
     */
    new cdk.CfnOutput(this, 'AlbDnsName', {
      value: loadBalancer.loadBalancerDnsName,
      description: 'DNS name of the Application Load Balancer',
      exportName: 'InvoiceMeAlbDnsName',
    });

    new cdk.CfnOutput(this, 'ApiUrl', {
      value: `https://${domainName}/api`,
      description: 'Base URL for the InvoiceMe API (HTTPS)',
    });

    new cdk.CfnOutput(this, 'HealthCheckUrl', {
      value: `https://${domainName}/api/health`,
      description: 'Health check endpoint URL',
    });
  }
}

