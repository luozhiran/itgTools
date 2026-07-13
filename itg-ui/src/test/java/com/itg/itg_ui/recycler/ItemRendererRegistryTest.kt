package com.itg.itg_ui.recycler

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.viewbinding.ViewBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemRendererRegistryTest {

    @Test
    fun registry_assignsDifferentViewTypesAndResolvesByExactClass() {
        val userRenderer = renderer<UserRow>()
        val bannerRenderer = renderer<BannerRow>()
        val registry = ItemRendererRegistryBuilder<Unit>().apply {
            renderer(userRenderer)
            renderer(bannerRenderer)
        }.build()

        val user = registry.rendererFor(UserRow(1, "A"))
        val banner = registry.rendererFor(BannerRow(2, "url"))

        assertSame(userRenderer, user.renderer)
        assertSame(bannerRenderer, banner.renderer)
        assertNotEquals(user.viewType, banner.viewType)
    }

    @Test
    fun registry_rejectsEmptyDuplicateAndUnknownRegistrations() {
        assertThrows(IllegalArgumentException::class.java) {
            ItemRendererRegistryBuilder<Unit>().build()
        }
        assertThrows(IllegalArgumentException::class.java) {
            ItemRendererRegistryBuilder<Unit>().apply {
                renderer(renderer<UserRow>())
                renderer(renderer<UserRow>())
            }.build()
        }
        val registry = ItemRendererRegistryBuilder<Unit>().apply {
            renderer(renderer<UserRow>())
        }.build()
        assertThrows(IllegalArgumentException::class.java) {
            registry.rendererFor(BannerRow(2, "url"))
        }
    }

    @Test
    fun diff_usesClassStableIdContentAndPayload() {
        val registry = ItemRendererRegistryBuilder<Unit>().apply {
            renderer(object : TestRenderer<UserRow>(UserRow::class.java) {
                override fun getChangePayload(oldItem: UserRow, newItem: UserRow): Any = "name"
            })
            renderer(renderer<BannerRow>())
        }.build()
        val diff = ItgItemDiffCallback(registry)
        val old = UserRow(1, "old")
        val same = UserRow(1, "old")
        val changed = UserRow(1, "new")

        assertTrue(diff.areItemsTheSame(old, same))
        assertTrue(diff.areContentsTheSame(old, same))
        assertFalse(diff.areContentsTheSame(old, changed))
        assertEquals("name", diff.getChangePayload(old, changed))
        assertFalse(diff.areItemsTheSame(old, BannerRow(1, "url")))
    }

    @Test
    fun stableIds_mustBeUniqueAcrossItemTypes() {
        val registry = ItemRendererRegistryBuilder<Unit>().apply {
            renderer(renderer<UserRow>())
            renderer(renderer<BannerRow>())
        }.build()
        assertThrows(IllegalArgumentException::class.java) {
            requireUniqueItemKeys(listOf(UserRow(1, "A"), BannerRow(1, "url")), registry)
        }
        requireUniqueItemKeys(listOf(UserRow(1, "A"), BannerRow(2, "url")), registry)
    }

    @Test
    fun businessItems_useRendererKeyWithoutImplementingItgListItem() {
        val renderer = TestRenderer(
            itemClass = BusinessRow::class.java,
            itemKey = { it.businessId },
        )
        val registry = ItemRendererRegistryBuilder<Unit>().apply {
            renderer(renderer)
        }.build()
        val diff = ItgItemDiffCallback(registry)
        val old = BusinessRow("order-1", "old")
        val changed = BusinessRow("order-1", "new")

        assertTrue(diff.areItemsTheSame(old, changed))
        assertFalse(diff.areContentsTheSame(old, changed))
        requireUniqueItemKeys(listOf(old), registry)
        assertThrows(IllegalArgumentException::class.java) {
            requireUniqueItemKeys(listOf(old, changed), registry)
        }

        val stableIds = ItemStableIdStore(registry)
        assertEquals(stableIds.idFor(old), stableIds.idFor(changed))
        assertNotEquals(stableIds.idFor(old), stableIds.idFor(BusinessRow("order-2", "new")))
    }

    private inline fun <reified I : ItgListItem> renderer(): TestRenderer<I> =
        TestRenderer(I::class.java)

    private open class TestRenderer<I : Any>(
        itemClass: Class<I>,
        itemKey: (I) -> Any = { defaultItemKey(it) },
    ) : ItemRenderer<I, ViewBinding, Unit>(itemClass, itemKey) {
        override fun createBinding(inflater: LayoutInflater, parent: ViewGroup): ViewBinding =
            error("Not used by this unit test")

        override fun bind(
            binding: ViewBinding,
            item: I,
            actions: Unit,
            lifecycleOwner: LifecycleOwner?,
            payloads: List<Any>,
        ) = Unit
    }

    private data class UserRow(
        override val stableId: Long,
        val name: String,
    ) : ItgListItem

    private data class BannerRow(
        override val stableId: Long,
        val url: String,
    ) : ItgListItem

    private data class BusinessRow(
        val businessId: String,
        val title: String,
    )
}
