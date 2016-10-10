package com.manoeuvres.android;

import android.content.Intent;
import android.os.Bundle;

//Support library.
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;

//Widgets
import android.widget.Toast;

//FacebookSDK
import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;

//Google mobile services.
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

//Firebase authentication.
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

//Firebase database.
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

//Models
import com.manoeuvres.android.models.Move;

//JSON
import org.json.JSONException;
import org.json.JSONObject;


public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivityLog";   //For logging.

    //Part of FacebookSDK. Receives a callback when the login for
    //Facebook button completes its tasks.
    private CallbackManager mCallbackManager;

    //Once the user logs in, these two fields will be retrieved
    //through a Facebook GraphRequest and stored into the
    //Firebase database.
    private long mFacebookUserId;
    private String mName;

    //The user needs to be authenticated with the Firebase authentication
    //system after logging into Facebook.
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;

    //The user's basic profile will be uploaded into the Firebase database,
    //and some default data app-related data will be set-up.
    private FirebaseDatabase mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Initialize Facebook SDK. This should be called as early as possible.
        FacebookSdk.sdkInitialize(getApplicationContext());

        setContentView(R.layout.activity_login);

        // Initialize Firebase Auth and create a listener for it.
        mAuth = FirebaseAuth.getInstance();
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                final FirebaseUser user = firebaseAuth.getCurrentUser();

                if (user != null) {

                    //Get database to create a new user or update access token of an existing user.
                    mDatabase = FirebaseDatabase.getInstance();

                    //Search for the signed-in user in the database.
                    mDatabase.getReference("users").addListenerForSingleValueEvent(new ValueEventListener() {

                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            //If the user already exists, start the MainActivity.
                            if (dataSnapshot.hasChild(user.getUid())) {
                                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                                startActivity(intent);
                                finish();
                            }
                            //Else, create new user profile.
                            else {
                                addUserToDatabase(user);
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {

                        }
                    });
                } else {
                    //User signed out.
                }
            }
        };

        mCallbackManager = CallbackManager.Factory.create();

        // Initialize Facebook Login button
        LoginButton loginButton = (LoginButton) findViewById(R.id.button_login_facebook);
        loginButton.setReadPermissions("email", "public_profile", "user_friends");
        loginButton.registerCallback(mCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {

                //Access token is first retrieved here.
                handleFacebookAccessToken(loginResult.getAccessToken());
            }

            @Override
            public void onCancel() {

            }

            @Override
            public void onError(FacebookException error) {

            }
        });
    }

    private void handleFacebookAccessToken(final AccessToken token) {

        //Use the access token to get the name and id of the user
        //through a GraphRequest and upload it to the database.
        setFacebookUserIdAndName();

        //Authenticate the user with the Firebase authentication using the access token.
        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {

                        // If sign in fails, display a message to the user. If sign in succeeds
                        // the auth state listener will be notified and logic to handle the
                        // signed in user can be handled in the listener.
                        if (!task.isSuccessful()) {
                            Toast.makeText(LoginActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        //When the user clicks on Facebook login button, a new activity is
        //started and the login result is sent back to the login activity.
        //The result will automatically be handled by the Facebook SDK,
        //just pass the result to the callback manager associated to the
        //login button.
        mCallbackManager.onActivityResult(requestCode, resultCode, data);
    }

    //Called when the user signs-in for the first time.
    private void addUserToDatabase(final FirebaseUser firebaseUser) {

        //Create profile
        DatabaseReference userProfileReference = mDatabase.getReference("users").child(firebaseUser.getUid());
        userProfileReference.child("facebookId").setValue(mFacebookUserId);
        userProfileReference.child("name").setValue(mName);
        userProfileReference.child("online").setValue(false);
        userProfileReference.child("lastSeenAt").setValue(System.currentTimeMillis());

        //Create moves
        DatabaseReference userMovesReference = mDatabase.getReference("moves").child(firebaseUser.getUid());
        userMovesReference.push().setValue(new Move("Drive", "Driving", "Drove"));
        userMovesReference.push().setValue(new Move("Eat", "Eating", "Ate"));
        userMovesReference.push().setValue(new Move("Relax", "Relaxing", "Relaxed"));
        userMovesReference.push().setValue(new Move("Sleep", "Sleeping", "Slept"));
        userMovesReference.push().setValue(new Move("Study", "Studying", "Studied"));
        userMovesReference.push().setValue(new Move("Work", "Working", "Worked"));

        //Start MainActivity.
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    //Called to get the Facebook id and name of the signed-in user
    //using the access token. These basic profile details will
    //be updated to the firebase database.
    private void setFacebookUserIdAndName() {
        GraphRequest request = GraphRequest.newMeRequest(
                AccessToken.getCurrentAccessToken(),
                new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(JSONObject object, GraphResponse response) {
                        try {
                            mFacebookUserId = object.getLong("id");
                            mName = object.getString("name");
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });

        Bundle parameters = new Bundle();
        parameters.putString("fields", "id,name");
        request.setParameters(parameters);
        request.executeAsync();
    }


    @Override
    protected void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
    }
}
