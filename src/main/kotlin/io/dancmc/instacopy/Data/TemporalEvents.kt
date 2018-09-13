package io.dancmc.instacopy.Data

class TemporalEvents {

    open class BaseTemporalEvent(var timestamp: Long)

    class CommentTemporalEvent(timestamp: Long,
                               var photoID: String,
                               var commenterName: String,
                               var commenterID: String,
                               var text: String) : BaseTemporalEvent(timestamp)

    class LikeTemporalEvent(timestamp: Long,
                            var photoID: String,
                            var likerName: String,
                            var likerID: String) : BaseTemporalEvent(timestamp)

    class FollowTemporalEvent(timestamp: Long,
                              var sourceName: String,
                              var sourceProfileName:String,
                              var sourceID: String,
                              var sinkName: String,
                              var sinkProfileName:String,
                              var sinkID: String,
                              var areFollowing: Boolean) : BaseTemporalEvent(timestamp)

    class RequestTemporalEvent(timestamp: Long,
                               var sourceName: String,
                               var sourceProfileName:String,
                               var sourceID: String,
                               var sinkName: String,
                               var sinkProfileName:String,
                               var sinkID: String,
                               var areFollowing:Boolean) : BaseTemporalEvent(timestamp)

}