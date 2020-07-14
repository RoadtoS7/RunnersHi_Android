package com.team.runnershi

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.team.runnershi.rank.RankFragment

class HomePagerAdapter(fm : FragmentManager) : FragmentPagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT){
    override fun getItem(position: Int): Fragment {
        return when(position) {
            0 -> HomeFragment()
            1 -> RecBadgeFragment()
            2 -> RankFragment()
            else -> MyProfileFragment()
        }
    }

    override fun getCount() = 4
}