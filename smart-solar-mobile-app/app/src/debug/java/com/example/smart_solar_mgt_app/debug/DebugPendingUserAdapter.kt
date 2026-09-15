package com.example.smart_solar_mgt_app.debug

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_solar_mgt_app.R
import com.example.smart_solar_mgt_app.domain.model.User
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class DebugPendingUserAdapter(
    private val onActivate: (User) -> Unit
) : ListAdapter<User, DebugPendingUserAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_debug_pending_user, parent, false)
        return ViewHolder(view as MaterialCardView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onActivate)
    }

    class ViewHolder(private val card: MaterialCardView) : RecyclerView.ViewHolder(card) {
        private val tvName: TextView = card.findViewById(R.id.tvDebugUserName)
        private val tvNic: TextView = card.findViewById(R.id.tvDebugUserNic)
        private val tvEmail: TextView = card.findViewById(R.id.tvDebugUserEmail)
        private val btnActivate: MaterialButton = card.findViewById(R.id.btnDebugActivate)

        fun bind(user: User, onActivate: (User) -> Unit) {
            tvName.text = user.name
            tvNic.text = "NIC: ${user.nic}"
            tvEmail.text = "Email: ${user.email}"
            btnActivate.setOnClickListener { onActivate(user) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User) = oldItem.nic == newItem.nic
        override fun areContentsTheSame(oldItem: User, newItem: User) = oldItem == newItem
    }
}
