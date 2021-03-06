/*
 * Copyright (c) 2019 Naman Dwivedi.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
package com.naman14.timberx.ui.fragments

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL
import com.naman14.timberx.R
import com.naman14.timberx.databinding.FragmentArtistDetailBinding
import com.naman14.timberx.models.Artist
import com.naman14.timberx.models.Song
import com.naman14.timberx.repository.AlbumRepository
import com.naman14.timberx.ui.adapters.AlbumAdapter
import com.naman14.timberx.ui.adapters.SongsAdapter
import com.naman14.timberx.util.AutoClearedValue
import com.naman14.timberx.util.Constants.ARTIST
import com.naman14.timberx.util.doAsyncPostWithResult
import com.naman14.timberx.util.extensions.addOnItemClick
import com.naman14.timberx.util.extensions.getExtraBundle
import com.naman14.timberx.util.extensions.toSongIds
import kotlinx.android.synthetic.main.fragment_artist_detail.*

class ArtistDetailFragment : MediaItemFragment() {

    lateinit var artist: Artist

    var binding by AutoClearedValue<FragmentArtistDetailBinding>(this)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(
                inflater, R.layout.fragment_artist_detail, container, false)

        artist = arguments?.get(ARTIST) as? Artist ?: throw IllegalStateException("No artist found in args.")

        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.artist = artist

        val adapter = SongsAdapter().apply {
            popupMenuListener = mainViewModel.popupMenuListener
        }

        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = adapter

        mediaItemFragmentViewModel.mediaItems.observe(this,
                Observer<List<MediaBrowserCompat.MediaItem>> { list ->
                    val isEmptyList = list?.isEmpty() ?: true
                    if (!isEmptyList) {
                        @Suppress("UNCHECKED_CAST")
                        adapter.updateData(list as ArrayList<Song>)
                    }
                })

        recyclerView.addOnItemClick { position: Int, _: View ->
            val extras = getExtraBundle(adapter.songs.toSongIds(), artist.name)
            mainViewModel.mediaItemClicked(adapter.songs[position], extras)
        }

        setupArtistAlbums()
    }

    private fun setupArtistAlbums() {
        val adapter = AlbumAdapter(true)

        rvArtistAlbums.layoutManager = LinearLayoutManager(activity, HORIZONTAL, false)
        rvArtistAlbums.adapter = adapter

        rvArtistAlbums.addOnItemClick { position: Int, _: View ->
            mainViewModel.mediaItemClicked(adapter.albums[position], null)
        }

        doAsyncPostWithResult(handler = {
            AlbumRepository.getAlbumsForArtist(activity!!, artist.id)
        }, postHandler = { albums ->
            albums?.let { adapter.updateData(it) }
        }).execute()
    }
}
