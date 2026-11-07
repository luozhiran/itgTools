cmake_minimum_required(VERSION 3.22.1)
project(itg_rn_business_codegen)

# REACT_ANDROID_DIR is supplied by the Gradle module; generated C++ is committed
# under codegen/ because this AAR is published without running Codegen in the host.
include(${REACT_ANDROID_DIR}/../ReactCommon/cmake-utils/react-native-flags.cmake)
include(${REACT_ANDROID_DIR}/cmake-utils/folly-flags.cmake)

find_package(ReactAndroid REQUIRED CONFIG)
find_package(fbjni REQUIRED CONFIG)
add_library(jsi ALIAS ReactAndroid::jsi)
add_library(reactnative ALIAS ReactAndroid::reactnative)
add_library(fbjni ALIAS fbjni::fbjni)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../codegen/jni codegen_build)
add_library(common_flags INTERFACE)
target_compile_options(common_flags INTERFACE ${folly_FLAGS})
target_link_libraries(react_codegen_ItgRnBusinessSpec common_flags)

add_library(itg_rn_business_codegen SHARED ItgRnBusinessOnLoad.cpp)
target_include_directories(itg_rn_business_codegen PRIVATE
  ${CMAKE_CURRENT_SOURCE_DIR}/../../../codegen/jni
  ${CMAKE_CURRENT_SOURCE_DIR}/../../../codegen/jni/react/renderer/components/ItgRnBusinessSpec)
target_link_libraries(itg_rn_business_codegen
  react_codegen_ItgRnBusinessSpec common_flags fbjni jsi reactnative)
target_compile_reactnative_options(itg_rn_business_codegen PRIVATE)
