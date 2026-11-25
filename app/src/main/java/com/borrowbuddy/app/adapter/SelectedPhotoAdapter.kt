package com.borrowbuddy.app.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.borrowbuddy.app.databinding.ItemSelectedPhotoBinding

class SelectedPhotoAdapter(
    private val photos: MutableList<Uri>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<SelectedPhotoAdapter.PhotoViewHolder>() {

    inner class PhotoViewHolder(val binding: ItemSelectedPhotoBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemSelectedPhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val uri = photos[position]
        holder.binding.ivPhotoThumbnail.setImageURI(uri)
        holder.binding.btnRemovePhoto.setOnClickListener {
            val currentPos = holder.bindingAdapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                onRemove(currentPos)
            }
        }
    }

    override fun getItemCount(): Int = photos.size
}
