package com.konami.ailens.main

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.konami.ailens.R
import com.konami.ailens.databinding.FragmentMainBinding
import com.konami.ailens.navigation.AddressPickerFragment

class MainFragment: Fragment() {
    private lateinit var binding: FragmentMainBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMainBinding.inflate(inflater, container, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            binding.main.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                bottomInset
            )
            insets
        }
        val value = resources.displayMetrics.density
        val tabLayout = binding.tabLayout
        val viewPager = binding.viewPager


        val adapter = MainPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->

            val view = LayoutInflater.from(context).inflate(R.layout.main_tab_item, null)
            val imageView = view.findViewById<ImageView>(R.id.imageView)
            val textView = view.findViewById<TextView>(R.id.textView)

            textView.text = when (position) {
                0 -> getString(R.string.main_tab_home)
                1 -> getString(R.string.main_tab_me)
                else -> ""
            }

            val selectedColor = requireContext().themeColor(R.attr.appPrimary)
            val normalColor = requireContext().themeColor(R.attr.appTextPlaceholder)

            if (position == 0) {
                imageView.setImageResource(R.drawable.ic_main_tab_home)
                textView.setTextColor(selectedColor)
            } else {
                imageView.setImageResource(R.drawable.ic_main_tab_me_unselected)
                textView.setTextColor(normalColor)
            }

            tab.customView = view
        }.attach()

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val view = tab.customView ?: return
                val position = tab.position
                val imageView = view.findViewById<ImageView>(R.id.imageView)
                val textView = view.findViewById<TextView>(R.id.textView)
                val color = requireContext().themeColor(R.attr.appPrimary)
                textView.setTextColor(color)
                if (position == 0) {
                    imageView.setImageResource(R.drawable.ic_main_tab_home)
                } else {
                    imageView.setImageResource(R.drawable.ic_main_tab_me)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                val view = tab.customView ?: return
                val position = tab.position
                val imageView = view.findViewById<ImageView>(R.id.imageView)
                val textView = view.findViewById<TextView>(R.id.textView)
                val color = requireContext().themeColor(R.attr.appTextPlaceholder)
                textView.setTextColor(color)
                if (position == 0) {
                    imageView.setImageResource(R.drawable.ic_main_tab_home_unselected)
                } else {
                    imageView.setImageResource(R.drawable.ic_main_tab_me_unselected)
                }
            }

            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        return binding.root
    }
}

class MainPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()
            1 -> HomeFragment()
            else -> Fragment()
        }
    }
}

private fun Context.themeColor(attrRes: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrRes, typedValue, true)
    return typedValue.data
}