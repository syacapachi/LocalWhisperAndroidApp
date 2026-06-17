//プロジェクト全体のビルド設定を管理します。

// どこからダウンロードするか
pluginManagement {
    // プラグイン倉庫一覧
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// ヘッダー定義、include
rootProject.name = "ProgrammingJissen2"
// ビルドに含めるもの
include(":app")
 