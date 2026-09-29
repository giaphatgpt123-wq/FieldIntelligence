pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "FieldIntelligence"
include(":app", ":domain:emergency", ":data:emergency", ":feature:emergency", ":duongodau-app")
