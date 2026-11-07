#include <jni.h>
#include <DefaultComponentsRegistry.h>
#include <DefaultTurboModuleManagerDelegate.h>
#include <ItgRnBusinessSpec.h>
#include <react/renderer/components/ItgRnBusinessSpec/ComponentDescriptors.h>

#include <functional>
#include <memory>
#include <string>

namespace {
using namespace facebook::react;

decltype(DefaultComponentsRegistry::registerCodegenComponentDescriptorsFromEntryPoint)
    previousComponents;
decltype(DefaultTurboModuleManagerDelegate::javaModuleProvider)
    previousJavaModules;
bool installed = false;
} // namespace

// RN's appmodules library is loaded by loadReactNative(). Install afterwards so
// this precompiled AAR can add its providers without replacing the host's ones.
extern "C" JNIEXPORT void JNICALL
Java_com_itg_rnbussiness_nativecode_ItgRnBusinessNative_nativeInstall(
    JNIEnv*, jobject) {
  if (installed) {
    return;
  }
  previousComponents =
      DefaultComponentsRegistry::registerCodegenComponentDescriptorsFromEntryPoint;
  DefaultComponentsRegistry::registerCodegenComponentDescriptorsFromEntryPoint =
      [](std::shared_ptr<const ComponentDescriptorProviderRegistry> registry) {
        if (previousComponents) {
          previousComponents(registry);
        }
        ItgRnBusinessSpec_registerComponentDescriptorsFromCodegen(registry);
      };

  previousJavaModules = DefaultTurboModuleManagerDelegate::javaModuleProvider;
  DefaultTurboModuleManagerDelegate::javaModuleProvider =
      [](const std::string& name, const JavaTurboModule::InitParams& params) {
        if (auto module = ItgRnBusinessSpec_ModuleProvider(name, params)) {
          return module;
        }
        return previousJavaModules ? previousJavaModules(name, params) : nullptr;
      };
  installed = true;
}
