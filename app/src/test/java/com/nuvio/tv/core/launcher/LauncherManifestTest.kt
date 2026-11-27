package com.nuvio.tv.core.launcher

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Guard the task identity shared by native HOME, accessibility, and every selectable app icon. The launcher's own
 * manifest entries live in the launcher-manifest module and are merged into the app's at build time, so both files
 * are read here.
 */
class LauncherManifestTest {
    private fun parse(vararg candidates: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(candidates.map(::File).first { it.isFile })

    private val appManifest by lazy { parse("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml") }
    private val launcherManifest by lazy {
        parse("../launcher-manifest/src/main/AndroidManifest.xml", "launcher-manifest/src/main/AndroidManifest.xml")
    }
    private val manifests get() = listOf(appManifest, launcherManifest)

    @Test
    fun `Home activity reuses its existing task`() {
        val main = elements("activity").single { it.androidAttribute("name") == ".MainActivity" }
        assertEquals("singleTask", main.androidAttribute("launchMode"))
        val categories = mainActivities().flatMap { descendants(it, "category") }
        assertTrue(categories.any { it.androidAttribute("name") == "android.intent.category.HOME" })
    }

    @Test
    fun `every app icon is an alias of the stable Home activity`() {
        val aliases = elements("activity-alias").filter { it.androidAttribute("name").startsWith(".launcher.AppIcon") }
        assertEquals(6, aliases.size)
        aliases.forEach { alias ->
            assertEquals(".MainActivity", alias.androidAttribute("targetActivity"))
        }
        assertTrue(elements("activity").none { it.androidAttribute("name").startsWith(".launcher.AppIcon") })
    }

    @Test
    fun `existing icon component names are preserved for installed shortcuts`() {
        assertEquals(
            setOf("Default", "ArcticBlue", "Emerald", "RoseGold", "Copper", "Graphite")
                .map { ".launcher.AppIcon$it" }.toSet(),
            elements("activity-alias").filter { it.androidAttribute("name").startsWith(".launcher.AppIcon") }
                .map { it.androidAttribute("name") }.toSet()
        )
    }

    @Test
    fun `Home filter includes default and main with an exported activity`() {
        val main = elements("activity").single { it.androidAttribute("name") == ".MainActivity" }
        assertEquals("true", main.androidAttribute("exported"))
        val filters = mainActivities().flatMap { descendants(it, "intent-filter") }
        val home = filters.single {
            descendants(it, "category").any { c -> c.androidAttribute("name") == "android.intent.category.HOME" }
        }
        assertEquals("android.intent.action.MAIN", (home.getElementsByTagName("action").item(0) as Element).androidAttribute("name"))
        val categories = home.getElementsByTagName("category")
        assertTrue((0 until categories.length).any {
            (categories.item(it) as Element).androidAttribute("name") == "android.intent.category.DEFAULT"
        })
    }

    @Test
    fun `accessibility binds through Android permission and requests no window content`() {
        val service = elements("service").single { it.androidAttribute("name") == "com.nuvio.tv.core.launcher.LauncherAccessibilityService" }
        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE", service.androidAttribute("permission"))
        val config = listOf(File("../launcher-manifest/src/main/res/xml/launcher_accessibility_service_config.xml"),
            File("launcher-manifest/src/main/res/xml/launcher_accessibility_service_config.xml")).first { it.isFile }
        val root = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(config).documentElement
        assertEquals("true", root.androidAttribute("canRequestFilterKeyEvents"))
        assertEquals("false", root.androidAttribute("canRetrieveWindowContent"))
    }

    /** Elements of the app's manifest and of the launcher module's. */
    private fun elements(tag: String): List<Element> = manifests.flatMap { descendants(it.documentElement, tag) }

    /** The Home activity as the two manifests declare it (the module one is merged into the app's). */
    private fun mainActivities(): List<Element> = elements("activity").filter {
        it.androidAttribute("name") in setOf(".MainActivity", "com.nuvio.tv.MainActivity")
    }

    private fun descendants(root: Element, tag: String): List<Element> {
        val nodes = root.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS("http://schemas.android.com/apk/res/android", name)
}
