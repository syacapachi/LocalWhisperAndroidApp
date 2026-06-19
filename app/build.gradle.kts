plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "jp.ac.gifu_u.programmingjissen2"

    compileSdk = 36

    defaultConfig {
        applicationId = "jp.ac.gifu_u.programmingjissen2"
        minSdk = 30
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                // 左から
                // -std=c++17 : G++17以上、
                // -O3 : 積極的な最適化(O3)(エラーが出たらO2(推奨最適化))、
                // -ffast-math : 浮動小数点の精度減らして高速化,
                // -fno-finite-math-only : ただしNaN・Infは存在する,
                // -fomit-frame-pointer : rbp(通常関数の呼び出し履歴を管理) を汎用レジスタとして利用(x86-64環境)(実は -O2以上で有効)
                // -fvectorize : Clangオプション、forをベクトル化(並列実行)(実は -O3で有効)
                // -fslp-vectorize : Clangオプション、for以外の連続した演算をベクトル化
                // extra  -march=armv8.2-a+fp16+dotprod : armv8.2以降のCPUが持つ命令,fp16(32bitではなく、16bit浮動小数点で計算),dotprod(演算の行列化＋並列化)を使う(環境を絞って高速化)
                // extra2 -march=armv8.6-a+i8mm+fp16+dotprod : armv8.6以降CPUまで制限し、i8mm(演算を16x16の行列化＋並列化)
                cppFlags += "-std=c++17 -O3 -ffast-math -fno-finite-math-only -fomit-frame-pointer -fvectorize -fslp-vectorize"


                arguments
                    "-DCMAKE_BUILD_TYPE=Release"
            }
        }

        ndk {
            // 実行環境をARM6
            abiFilters += "arm64-v8a"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/Whisper/CMakeLists.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
