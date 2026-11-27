package com.nuvio.tv.core.apps

/**
 * The Apps row's key in the Home rows' order list. It sits in the same family as collection keys
 * ("collection_...") on purpose: the reorder screen, the "follow addons' order" mode and the row
 * placement all already know how to move such a row between catalog blocks, so Apps moves like any row.
 */
const val INSTALLED_APPS_CATALOG_KEY = "collection_installed_apps"

/**
 * Until the user has placed the Apps row, Home shows it first (right under Continue Watching): the same rule for the
 * list of catalogs in Reorder Home Catalogs, so what it shows matches Home. Returns [order] with the row moved first.
 */
fun launcherAppsFirst(order: List<String>, savedOrderKeys: List<String>): List<String> =
    if (INSTALLED_APPS_CATALOG_KEY !in savedOrderKeys && INSTALLED_APPS_CATALOG_KEY in order) {
        listOf(INSTALLED_APPS_CATALOG_KEY) + order.filterNot { it == INSTALLED_APPS_CATALOG_KEY }
    } else order

/** The same rule applied to Home's own working list of catalog keys. */
fun moveLauncherAppsFirst(order: MutableList<String>, savedOrderKeys: List<String>) {
    if (INSTALLED_APPS_CATALOG_KEY !in savedOrderKeys && order.remove(INSTALLED_APPS_CATALOG_KEY)) order.add(0, INSTALLED_APPS_CATALOG_KEY)
}
