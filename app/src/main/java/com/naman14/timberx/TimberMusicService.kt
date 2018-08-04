package com.naman14.timberx

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaPlayer
import java.util.*
import android.media.browse.MediaBrowser
import android.net.Uri
import android.os.*
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.annotation.Nullable
import androidx.media.MediaBrowserServiceCompat
import com.naman14.timberx.repository.SongsRepository
import com.naman14.timberx.vo.Song
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver
import com.naman14.timberx.util.*
import android.provider.MediaStore
import java.io.FileNotFoundException

class TimberMusicService: MediaBrowserServiceCompat(), MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {

    val MEDIA_ID_ROOT = "__ROOT__"
    val TYPE_ARTIST = 0
    val TYPE_ALBUM = 1
    val TYPE_SONG = 2
    val TYPE_PLAYLIST = 3
    val TYPE_ARTIST_SONG_ALBUMS = 4
    val TYPE_ALBUM_SONGS = 5
    val TYPE_ARTIST_ALL_SONGS = 6
    val TYPE_PLAYLIST_ALL_SONGS = 7

    val NOTIFICATION_ID = 888

    var mCurrentSongId: Long = -1
    var isPlaying = false
    var isInitialized = false
    var mStarted = false

    private lateinit var mMediaSession: MediaSessionCompat
    private lateinit var mStateBuilder: PlaybackStateCompat.Builder
    private lateinit var mMetadataBuilder: MediaMetadataCompat.Builder

    private var player: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()

        setUpMediaSession()

        mMediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)

        mStateBuilder = PlaybackStateCompat.Builder().setActions(
                        PlaybackStateCompat.ACTION_PLAY
                                or PlaybackStateCompat.ACTION_PLAY_PAUSE
                                or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                                or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        mMediaSession.setPlaybackState(mStateBuilder.build())

        mMetadataBuilder = MediaMetadataCompat.Builder()

        sessionToken = mMediaSession.sessionToken


        player = MediaPlayer()
        player?.setWakeMode(applicationContext,
                    PowerManager.PARTIAL_WAKE_LOCK)
        player?.setAudioStreamType(AudioManager.STREAM_MUSIC)
        player?.setOnPreparedListener(this)
        player?.setOnCompletionListener(this)
        player?.setOnErrorListener(this)

    }

    private fun setUpMediaSession() {
        mMediaSession = MediaSessionCompat(this, "TimberX")
        mMediaSession.setCallback(object : MediaSessionCompat.Callback() {

            override fun onPause() {
                pause()
            }

            override fun onPlay() {
                if (!mStarted) {
                    startService()
                }
                playSong()
            }

            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                if (!mStarted) {
                    startService()
                }
                playSong(mediaId!!.toLong())
            }

            override fun onSeekTo(pos: Long) {
                if (isInitialized) {
                    player?.seekTo(pos.toInt())
                }
            }

            override fun onSkipToNext() {

            }

            override fun onSkipToPrevious() {

            }

            override fun onStop() {
                stopService()
            }

            override fun onAddQueueItem(description: MediaDescriptionCompat?) {
                super.onAddQueueItem(description)
            }

            override fun onRemoveQueueItem(description: MediaDescriptionCompat?) {
                super.onRemoveQueueItem(description)
            }

            override fun onSetRepeatMode(repeatMode: Int) {
                super.onSetRepeatMode(repeatMode)
            }

            override fun onSetShuffleMode(shuffleMode: Int) {
                super.onSetShuffleMode(shuffleMode)
            }

        })
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mMediaSession, intent)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }


    override fun onPrepared(player: MediaPlayer?) {
        isPlaying = true
        setPlaybackState(mStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, 0,  1F).build())
        startForeground(NOTIFICATION_ID, NotificationUtils.buildNotification(this, mMediaSession))
        player?.start()
    }

    override fun onCompletion(player: MediaPlayer?) {

    }

    override fun onError(player: MediaPlayer?, p1: Int, p2: Int): Boolean {
        isPlaying = false
        return false
    }


    fun playSong(song: Song) {
        if (mCurrentSongId != song.id) {
            mCurrentSongId = song.id
            isInitialized = false
        }
        setMetaData(song)
        playSong()
    }

    fun playSong(id: Long) {
        val song = SongsRepository.getSongForId(this, id)
        playSong(song)
    }

    fun playSong() {
        if (isInitialized) {
            setPlaybackState(mStateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, mMediaSession.position(),  1F).build())
            startForeground(NOTIFICATION_ID, NotificationUtils.buildNotification(this, mMediaSession))
            player?.start()
            return
        }

        player?.reset()
        val path: String = getSongUri(mCurrentSongId).toString()
        if (path.startsWith("content://")) {
            player?.setDataSource(this, Uri.parse(path))
        } else {
            player?.setDataSource(path)
        }
        isInitialized = true
        player?.prepareAsync()
    }

    fun playPause(id: Long) {
        if (isPlaying) {
            pause()
        } else {
           playSong(id)
        }
    }

    fun pause() {
        if (isPlaying && isInitialized) {
            setPlaybackState(mStateBuilder.setState(PlaybackStateCompat.STATE_PAUSED, mMediaSession.position(),  1F).build())
            NotificationUtils.updateNotification(this, mMediaSession)
            player?.pause()
            stopForeground(false)
        }
    }

    fun position(): Int {
        return player?.currentPosition ?: 0
    }

    private fun startService() {
        if (!mStarted) {
            val intent = Intent(this, TimberMusicService::class.java)
            ContextCompat.startForegroundService(this, intent)
            startForeground(NOTIFICATION_ID, NotificationUtils.buildNotification(this, mMediaSession))
            mStarted = true
        }
    }

    private fun stopService() {
        if (mStarted) {
            stopSelf()
            mStarted = false
        }
    }

    private fun setPlaybackState(playbackStateCompat: PlaybackStateCompat) {
        mMediaSession.setPlaybackState(playbackStateCompat)
    }

    private fun setMetaData(song: Song) {
        var artwork: Bitmap? = null
        try {
            artwork = MediaStore.Images.Media.getBitmap(this.contentResolver, Utils.getAlbumArtUri(song.albumId))
        } catch (e: FileNotFoundException) {
            artwork = BitmapFactory.decodeResource(resources, R.drawable.icon)
        }

        val mediaMetadata = mMetadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork).build()
        mMediaSession.setMetadata(mediaMetadata)
    }

    //media browser
    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowserCompat.MediaItem>>) {
        result.detach()
        loadChildren(parentId, result)
    }

    @Nullable
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): MediaBrowserServiceCompat.BrowserRoot? {
        return MediaBrowserServiceCompat.BrowserRoot(MEDIA_ID_ROOT, null)
    }

    private fun addMediaRoots(mMediaRoot: MutableList<MediaBrowserCompat.MediaItem>) {
        mMediaRoot.add(MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                        .setMediaId(Integer.toString(TYPE_ARTIST))
                        .setTitle(getString(R.string.artists))
                        .setIconUri(Uri.parse(Utils.getEmptyAlbumArtUri()))
                        .setSubtitle(getString(R.string.artists))
                        .build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
        ))

        mMediaRoot.add(MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                        .setMediaId(Integer.toString(TYPE_ALBUM))
                        .setTitle(getString(R.string.albums))
                        .setIconUri(Uri.parse(Utils.getEmptyAlbumArtUri()))
                        .setSubtitle(getString(R.string.albums))
                        .build(), MediaBrowser.MediaItem.FLAG_BROWSABLE
        ))

        mMediaRoot.add(MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                        .setMediaId(Integer.toString(TYPE_SONG))
                        .setTitle(getString(R.string.songs))
                        .setIconUri(Uri.parse(Utils.getEmptyAlbumArtUri()))
                        .setSubtitle(getString(R.string.songs))
                        .build(), MediaBrowser.MediaItem.FLAG_BROWSABLE
        ))


        mMediaRoot.add(MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                        .setMediaId(Integer.toString(TYPE_PLAYLIST))
                        .setTitle(getString(R.string.playlists))
                        .setIconUri(Uri.parse(Utils.getEmptyAlbumArtUri()))
                        .setSubtitle(getString(R.string.playlists))
                        .build(), MediaBrowser.MediaItem.FLAG_BROWSABLE
        ))

    }

    private fun loadChildren(parentId: String, result: MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>>) {

        val mediaItems = ArrayList<MediaBrowserCompat.MediaItem>()

        doAsyncPost(handler = {
            if (parentId == MEDIA_ID_ROOT) {
                addMediaRoots(mediaItems)
            } else {
                when (Integer.parseInt(Character.toString(parentId[0]))) {
                    TYPE_ARTIST -> {
//                        val artistList = ArtistLoader.getAllArtists(mContext)
//                        for (artist in artistList) {
//                            val albumNmber = TimberUtils.makeLabel(mContext, R.plurals.Nalbums, artist.albumCount)
//                            val songCount = TimberUtils.makeLabel(mContext, R.plurals.Nsongs, artist.songCount)
//                            fillMediaItems(mediaItems, Integer.toString(TYPE_ARTIST_SONG_ALBUMS) + java.lang.Long.toString(artist.id), artist.name, Uri.parse("android.resource://" + "naman14.timber/drawable/ic_empty_music2"), TimberUtils.makeCombinedString(mContext, albumNmber, songCount), MediaBrowser.MediaItem.FLAG_BROWSABLE)
//                        }
                    }
                    TYPE_ALBUM -> {
//                        val albumList = AlbumLoader.getAllAlbums(mContext)
//                        for (album in albumList) {
//                            fillMediaItems(mediaItems, Integer.toString(TYPE_ALBUM_SONGS) + java.lang.Long.toString(album.id), album.title, TimberUtils.getAlbumArtUri(album.id), album.artistName, MediaBrowser.MediaItem.FLAG_BROWSABLE)
//                        }
                    }
                    TYPE_SONG -> {
                        val songList = SongsRepository.loadSongs(this)
                        for (song in songList) {
                            fillMediaItems(mediaItems, song.id.toString(), song.title, Utils.getAlbumArtUri(song.albumId), song.artist, MediaBrowser.MediaItem.FLAG_PLAYABLE)
                        }
                    }
                    TYPE_ALBUM_SONGS -> {
//                        val albumSongList = AlbumSongLoader.getSongsForAlbum(mContext, java.lang.Long.parseLong(parentId.substring(1)))
//                        for (song in albumSongList) {
//                            fillMediaItems(mediaItems, String.valueOf(song.id), song.title, TimberUtils.getAlbumArtUri(song.albumId), song.artistName, MediaBrowser.MediaItem.FLAG_PLAYABLE)
//                        }
                    }
                    TYPE_ARTIST_SONG_ALBUMS -> {
//                        fillMediaItems(mediaItems, Integer.toString(TYPE_ARTIST_ALL_SONGS) + java.lang.Long.parseLong(parentId.substring(1)), "All songs", Uri.parse("android.resource://" + "naman14.timber/drawable/ic_empty_music2"), "All songs by artist", MediaBrowser.MediaItem.FLAG_BROWSABLE)
//                        val artistAlbums = ArtistAlbumLoader.getAlbumsForArtist(mContext, java.lang.Long.parseLong(parentId.substring(1)))
//                        for (album in artistAlbums) {
//                            val songCount = TimberUtils.makeLabel(mContext, R.plurals.Nsongs, album.songCount)
//                            fillMediaItems(mediaItems, Integer.toString(TYPE_ALBUM_SONGS) + java.lang.Long.toString(album.id), album.title, TimberUtils.getAlbumArtUri(album.id), songCount, MediaBrowser.MediaItem.FLAG_BROWSABLE)
//
//                        }
                    }
                    TYPE_ARTIST_ALL_SONGS -> {
//                        val artistSongs = ArtistSongLoader.getSongsForArtist(mContext, java.lang.Long.parseLong(parentId.substring(1)))
//                        for (song in artistSongs) {
//                            fillMediaItems(mediaItems, String.valueOf(song.id), song.title, TimberUtils.getAlbumArtUri(song.albumId), song.albumName, MediaBrowser.MediaItem.FLAG_PLAYABLE)
//                        }
                    }
                    TYPE_PLAYLIST -> {
//                        val playlistList = PlaylistLoader.getPlaylists(mContext, false)
//                        for (playlist in playlistList) {
//                            val songCount = TimberUtils.makeLabel(mContext, R.plurals.Nsongs, playlist.songCount)
//                            fillMediaItems(mediaItems, Integer.toString(TYPE_PLAYLIST_ALL_SONGS) + java.lang.Long.toString(playlist.id), playlist.name,
//                                    Uri.parse("android.resource://" + "naman14.timber/drawable/ic_empty_music2"), songCount, MediaBrowser.MediaItem.FLAG_BROWSABLE)
//                        }
                    }
                    TYPE_PLAYLIST_ALL_SONGS -> {
//                        val playlistSongs = PlaylistSongLoader.getSongsInPlaylist(mContext, java.lang.Long.parseLong(parentId.substring(1)))
//                        for (song in playlistSongs) {
//                            fillMediaItems(mediaItems, String.valueOf(song.id), song.title, TimberUtils.getAlbumArtUri(song.albumId), song.albumName, MediaBrowser.MediaItem.FLAG_PLAYABLE)
//                        }
                    }
                }
            }
        }, postHandler = {
            result.sendResult(mediaItems)
        }).execute()


    }

    private fun fillMediaItems(mediaItems: MutableList<MediaBrowserCompat.MediaItem>, mediaId: String, title: String, icon: Uri, subTitle: String, playableOrBrowsable: Int) {
        mediaItems.add(MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder()
                        .setMediaId(mediaId)
                        .setTitle(title)
                        .setIconUri(icon)
                        .setSubtitle(subTitle)
                        .build(), playableOrBrowsable
        ))
    }
}