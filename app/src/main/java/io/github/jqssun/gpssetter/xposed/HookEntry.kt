package io.github.jqssun.gpssetter.xposed

import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import io.github.jqssun.gpssetter.BuildConfig

@InjectYukiHookWithXposed(modulePackageName = BuildConfig.APPLICATION_ID)
class HookEntry : IYukiHookXposedInit {

    override fun onInit() = configs {
        isEnableHookSharedPreferences = true
        isEnableModulePrefsCache = true
    }

    override fun onHook() = encase {
        loadHooker(LocationHook)
    }

}