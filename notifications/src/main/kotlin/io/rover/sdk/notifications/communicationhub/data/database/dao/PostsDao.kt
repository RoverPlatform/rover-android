/*
 * Copyright (c) 2023, Rover Labs, Inc. All rights reserved.
 * You are hereby granted a non-exclusive, worldwide, royalty-free license to use,
 * copy, modify, and distribute this software in source code or binary form for use
 * in connection with the web services and APIs provided by Rover.
 *
 * This copyright notice shall be included in all copies or substantial portions of
 * the software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.rover.sdk.notifications.communicationhub.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.rover.sdk.notifications.communicationhub.data.database.entities.PostEntity
import io.rover.sdk.notifications.communicationhub.data.database.entities.PostWithSubscription
import kotlinx.coroutines.flow.Flow

@Dao
interface PostsDao {
    @Query("SELECT * FROM posts ORDER BY receivedAt DESC")
    fun getAllPosts(): Flow<List<PostEntity>>
    
    @Transaction
    @Query("SELECT * FROM posts ORDER BY receivedAt DESC")
    fun getAllPostsWithSubscriptions(): Flow<List<PostWithSubscription>>
    
    @Query("SELECT * FROM posts WHERE id = :postId")
    suspend fun getPostById(postId: String): PostEntity?

    @Upsert
    suspend fun upsertPost(post: PostEntity)
    
    @Upsert
    suspend fun upsertPosts(posts: List<PostEntity>)
    
    @Query("DELETE FROM posts WHERE id = :postId")
    suspend fun deletePost(postId: String)
    
    @Query("UPDATE posts SET isRead = 1 WHERE id = :postId")
    suspend fun markPostAsRead(postId: String)
    
    @Query("SELECT COUNT(*) FROM posts WHERE isRead = 0")
    suspend fun getUnreadCount(): Int
}