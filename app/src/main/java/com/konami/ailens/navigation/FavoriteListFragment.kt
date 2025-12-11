package com.konami.ailens.navigation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.konami.ailens.FavoriteLocation
import com.konami.ailens.R
import com.konami.ailens.SharedPrefs
import com.konami.ailens.databinding.FragmentFavoriteListBinding
import com.konami.ailens.resolveAttrColor

class FavoriteListFragment: Fragment() {
    private lateinit var binding: FragmentFavoriteListBinding
    private lateinit var adapter: FavoriteListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentFavoriteListBinding.inflate(inflater, container, false)

        // Setup back button
        binding.backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        // Setup RecyclerView
        adapter = FavoriteListAdapter(
            onEditClick = { type, value ->
                // Navigate to edit screen
                val bundle = bundleOf("favoriteTypeOrdinal" to type.ordinal)
                bundle.putString("value", value)
                findNavController().navigate(
                    R.id.action_favoriteListFragment_to_favoriteEditFragment,
                    bundle
                )
            },
            onDeleteClick = { type ->
                // Delete favorite
                when (type) {
                    FavoriteType.HOME -> SharedPrefs.instance.homeFavorite = null
                    FavoriteType.COMPANY -> SharedPrefs.instance.companyFavorite = null
                }
                adapter.notifyDataSetChanged()
            },
            onAddClick = { type ->
                // Navigate to add screen
                val bundle = bundleOf("favoriteTypeOrdinal" to type.ordinal)
                findNavController().navigate(
                    R.id.action_favoriteListFragment_to_favoriteEditFragment,
                    bundle
                )
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        // Add divider
        val density = resources.displayMetrics.density
        val dividerHeight = (1 * density).toInt()
        val paddingHorizontal = resources.getDimension(R.dimen.favorite_list_item_padding_horizontal).toInt()
        val dividerColor = requireContext().resolveAttrColor(R.attr.appBorderDarkGray)
        val divider = VerticalDivider(
            height = dividerHeight,
            lineColor = dividerColor,
            paddingStart = paddingHorizontal,
            paddingEnd = paddingHorizontal
        )
        binding.recyclerView.addItemDecoration(divider)

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // Refresh the list when returning from edit screen
        adapter.notifyDataSetChanged()
    }

    enum class FavoriteType {
        HOME, COMPANY
    }

    data class FavoriteItem(
        val type: FavoriteType,
        val title: String,
        val favorite: FavoriteLocation?
    )

    class FavoriteListAdapter(
        private val onEditClick: (FavoriteType, String) -> Unit,
        private val onDeleteClick: (FavoriteType) -> Unit,
        private val onAddClick: (FavoriteType) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val VIEW_TYPE_SET = 0
            private const val VIEW_TYPE_UNSET = 1
        }

        // ViewHolder for set favorite items (favorite_list_item.xml)
        class SetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.bgImageView)
            val titleTextView: TextView = view.findViewById(R.id.titleTextView)
            val valueTextView: TextView = view.findViewById(R.id.valueTextView)
            val editButton: Button = view.findViewById(R.id.editButton)
            val deleteButton: ImageButton = view.findViewById(R.id.deleteButton)
        }

        // ViewHolder for unset favorite items (favorite_list_item_unset.xml)
        class UnsetViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.bgImageView)
            val titleTextView: TextView = view.findViewById(R.id.titleTextView)
            val valueTextView: TextView = view.findViewById(R.id.valueTextView)
            val addButton: Button = view.findViewById(R.id.addButton)
        }

        private fun getFavoriteItems(): List<FavoriteItem> {
            return listOf(
                FavoriteItem(
                    type = FavoriteType.HOME,
                    title = "Home",
                    favorite = SharedPrefs.instance.homeFavorite
                ),
                FavoriteItem(
                    type = FavoriteType.COMPANY,
                    title = "Company",
                    favorite = SharedPrefs.instance.companyFavorite
                )
            )
        }

        override fun getItemViewType(position: Int): Int {
            val item = getFavoriteItems()[position]
            return if (item.favorite != null) VIEW_TYPE_SET else VIEW_TYPE_UNSET
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                VIEW_TYPE_SET -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.favorite_list_item, parent, false)
                    SetViewHolder(view)
                }
                VIEW_TYPE_UNSET -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.favorite_list_item_unset, parent, false)
                    UnsetViewHolder(view)
                }
                else -> throw IllegalArgumentException("Unknown view type: $viewType")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = getFavoriteItems()[position]

            when (holder) {
                is SetViewHolder -> {
                    holder.titleTextView.text = item.title
                    // Display mainText and secondaryText
                    val addressText = item.favorite?.let {
                        "${it.mainText}, ${it.secondaryText}"
                    } ?: "Unset"
                    holder.valueTextView.text = addressText

                    // Set icon based on type
                    val iconRes = when (item.type) {
                        FavoriteType.HOME -> R.drawable.ic_favorite_list_home_active
                        FavoriteType.COMPANY -> R.drawable.ic_favorite_list_company_active
                    }
                    holder.imageView.setImageResource(iconRes)

                    // Set click listeners
                    holder.editButton.setOnClickListener {
                        onEditClick(item.type, addressText)
                    }
                    holder.deleteButton.setOnClickListener {
                        onDeleteClick(item.type)
                    }
                }
                is UnsetViewHolder -> {
                    holder.titleTextView.text = item.title
                    holder.valueTextView.text = "Unset"

                    // Set icon based on type
                    val iconRes = when (item.type) {
                        FavoriteType.HOME -> R.drawable.ic_favorite_list_home_inactive
                        FavoriteType.COMPANY -> R.drawable.ic_favorite_list_company_inactive
                    }
                    holder.imageView.setImageResource(iconRes)

                    // Set click listener
                    holder.addButton.setOnClickListener {
                        onAddClick(item.type)
                    }
                }
            }
        }

        override fun getItemCount() = getFavoriteItems().size
    }
}
