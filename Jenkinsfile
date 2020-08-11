@Library(value="kids-first/aws-infra-jenkins-shared-libraries", changelog=false) _
ecs_service_type_1_standard {
    projectName = "kf-api-search-members"
    environments = "dev,qa,prd"
    docker_image_type = "debian"
    entrypoint_command = "java -Dhttp.port=80 -jar kf-search-members.jar" 
    deploy_scripts_version = "master"
    quick_deploy = "true"
    internal_app = "false"
    external_config_repo = "false"
    container_port = "80"
    vcpu_container             = "2048"
    memory_container           = "4096"
    vcpu_task                  = "2048"
    memory_task                = "4096"
    health_check_path = "/"
    dependencies = "ecr,postgres_rds"
    friendly_dns_name = "search-members-api"
}
