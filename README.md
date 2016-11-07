# Overview
[Manoeuvres](https://goo.gl/HN2pHx) is an Android app to broadcast actions to your followers. The idea is to make it easier for you to let your friends or family know about what you are doing. You just have to select your <i>move</i>, and all your followers will be notified in real-time. This makes it faster than placing a call or opening a chat application to write and send a message.

#Features 
1. Find your Facebook friends who are using the app
2. Send/Cancel/Accept follow requests
3. Remove a follower/friend you are following
4. Start/Stop a <i>move</i>
5. See the history of moves of the friends you are following
6. New/Accepted request notifications
7. Notifications for moves of the friends you are following
8. Real-time (no swipe-to-refresh) :)

####Upcoming features
1. Custom/Add/Delete moves
2. Last seen at/Online status
3. Home-screen widget for moves
4. Privacy settings
5. Do not disturb
6. Attach image to a move
7. Broadcast live location with a move related to travelling

#Architecture
The app follows the <i>Model-View-Presenter</i> (MVP) architecture. The classes are packaged based on features.

##Models
![Models](http://i.imgur.com/LWX5JmK.png)

##Views
![Views](http://i.imgur.com/m7Km042.png)

##Presenters
* Presenters get raw data from the database and present it in a meaningful way to the views.
* There is **only one instance** of a particular presenter in the memory.
* **Multiple views can simultaneously get data from a presenter**. 
* They have a nested interface which can be implemented by views to get updates about any changes in the database.
* Views can attach/detach from a presenter, ask the presenters to start or stop data synchronization, perform actions on the database such as accept a request or follow a friend.
* A presenter automatically stops listening to database and **destroys itself if there is no view attached to it**.
* Each presenter also has a <i>DatabaseHelper</i> class which contains only static methods. The presenter delegates one-time reading/writing operations to these database helpers. 

###Getting data about friends
![FriendsPresenter](http://i.imgur.com/uW0KHiO.png)

###Getting data about friends' moves
![TimelinePresenter](http://i.imgur.com/0cPzie7.png)

###Listening for network changes
![NetworkMonitor](http://i.imgur.com/Wve8LlA.png)
* Technically, _NetworkMonitor_ is not a presenter since it's not presenting any data, but the concept is same.
* Any component can attach to it and get updates about any changes in the network state by implementing _NetworkListener_.
* It uses NetworkCallback for API > 20, and falls back to using a traditional _BroadcastReceiver_ for API < 21.

#Optimizations
* Memory-efficient collections<br />
![ArrayMap](http://i.imgur.com/Gwufx5f.png)
* Re-use instances <br />
![Re-UseInstances](http://i.imgur.com/nofYYF2.png)
* _for_ loop instead of for-each<br />
![for](http://i.imgur.com/zMlmfjK.png)
* Specify initial capacity<br />
![capacity](http://i.imgur.com/1N9pyZU.png)

#Backend
I've used [Firebase](https://firebase.google.com/) for its real-time database and authentication system. It stores the data in a NoSQL databse.
![Database](http://i.imgur.com/M5Hh5Rb.png) </br>
The database has a flat structure (minimal nesting), thus prevents redundant data transfers.
