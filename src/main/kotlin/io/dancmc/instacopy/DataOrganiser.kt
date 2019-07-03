package io.dancmc.instacopy

import io.dancmc.instacopy.Data.DataLoader
import io.dancmc.instacopy.Data.User
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileReader

class DataOrganiser {

    companion object {

        // This method executes on another thread/s, doesn't block main program
        fun execute() {

            GlobalScope.launch {

                // 1. Get picture urls (rate limit 50 pulls per access key/hr) and then verify all sizes have same images
//                  ApiDownloader.getUnsplashUrls(ApiDownloader.accessKey1, 1101..1110)
//                    println("All picture lists contain same IDs : ${ApiDownloader.checkUnsplashUrls()}")

//                 2. Get and save all pictures
//            ApiDownloader.getPictures("regular")
//                ApiDownloader.getPictures("small")
//                ApiDownloader.getPictures("thumb")

//                 3. Process json (from the Kaggle dataset) and write quotes to tsv
//                ApiDownloader.getQuotes()

                // 4. Save users from API to file
//                ApiDownloader.saveUsersFromApi(2000, "male")
//                ApiDownloader.saveUsersFromApi(2000, "female")

                // 5. Load locations from simplemaps csv file
//                ApiDownloader.processLocations()

                // 6. Run Dataloader and generate userlist and database
//                DataLoader.execute()

                // 7. Fetch profile photos according to userlist
//                ApiDownloader.fetchProfilePhotos(ApiDownloader.getProfilePicUrls())


            }
        }

    }
}