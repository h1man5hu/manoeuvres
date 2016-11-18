package com.manoeuvres.android.login;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.manoeuvres.android.R;
import com.manoeuvres.android.network.NetworkMonitor;
import com.manoeuvres.android.network.NetworkMonitor.NetworkListener;
import com.manoeuvres.android.database.CompletionListeners;
import com.manoeuvres.android.database.CompletionListeners.GetBooleanListener;
import com.manoeuvres.android.util.Constants;
import com.manoeuvres.android.core.MainActivity;

import org.json.JSONException;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity implements NetworkListener {

    private CallbackManager mCallbackManager;
    private long mFacebookUserId;
    private String mName;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private LoginButton mLoginButton;
    private ProgressBar mProgressBar;
    private TextView mLoadingTextView;
    private NetworkMonitor mNetworkMonitor;
    private boolean mIsAuthenticating;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FacebookSdk.sdkInitialize(getApplicationContext());
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                final FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    mLoadingTextView.setText(R.string.loading_login_check_account);

                    /*
                     * Check if the user is signing in or signing up.
                     * If the user is signing up, get the basic profile through a GraphRequest
                     * and initialize default app data. Otherwise, start the MainActivity.
                     */
                    AuthPresenter.checkIfUserExists(user.getUid(), new GetBooleanListener() {
                        @Override
                        public void onComplete(boolean exists) {
                            if (exists) {
                                mLoadingTextView.setText(R.string.loading_login_account_exists);
                                startActivity(MainActivity.class);
                            } else {
                                mLoadingTextView.setText(R.string.loading_login_creating_account);
                                AuthPresenter.registerUser(mFacebookUserId, mName, getResources(),
                                        new CompletionListeners.OnCompleteListener() {
                                            @Override
                                            public void onComplete() {
                                                mLoadingTextView.setText(
                                                        R.string.loading_login_account_created
                                                );
                                                startActivity(MainActivity.class);
                                            }

                                            @Override
                                            public void onFailed() {

                                            }
                                        });
                            }
                        }

                        @Override
                        public void onFailed() {

                        }
                    });
                } else {
                    LoginManager.getInstance().logOut();
                    mIsAuthenticating = false;
                }
            }
        };

        mProgressBar = (ProgressBar) findViewById(R.id.progressBarLogIn);
        mLoadingTextView = (TextView) findViewById(R.id.textViewLoading);
        mLoginButton = (LoginButton) findViewById(R.id.button_login_facebook);
        mLoginButton.setReadPermissions(
                Constants.FACEBOOK_PERMISSION_EMAIL,
                Constants.FACEBOOK_PERMISSION_PUBLIC_PROFILE,
                Constants.FACEBOOK_PERMISSION_USER_FRIENDS
        );

        mCallbackManager = CallbackManager.Factory.create();
        mLoginButton.registerCallback(mCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                /* Access token is first retrieved here. */
                handleFacebookAccessToken(loginResult.getAccessToken());
                mProgressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onCancel() {
                hideProgress();
                mIsAuthenticating = false;
            }

            @Override
            public void onError(FacebookException error) {
                hideProgress();
                mIsAuthenticating = false;
            }
        });

        mLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsAuthenticating = true;
                mLoginButton.setVisibility(View.INVISIBLE);
                mLoadingTextView.setText(R.string.loading_login_authenticate_facebook);
                mLoadingTextView.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        mAuth.addAuthStateListener(mAuthListener);
        mNetworkMonitor = NetworkMonitor.getInstance(getApplicationContext()).attach(this);
        if (mNetworkMonitor.isNetworkConnected()) {
            onNetworkConnected();
        } else {
            onNetworkDisconnected();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        /*
         * When the user clicks on Facebook login button, a new activity is started and the login
         * result is sent back to the login activity. The result will automatically be handled by
         * the Facebook SDK, just pass the result to the callback manager associated to the login
         * button.
         */
        mCallbackManager.onActivityResult(requestCode, resultCode, data);
    }

    private void handleFacebookAccessToken(final AccessToken token) {

        /*
         * Use the access token to get the name and id of the user through a GraphRequest and upload
         * it to the database.
         */
        setFacebookUserIdAndName();

        mLoadingTextView.setText(R.string.loading_login_authenticate_firebase);

        /* Authenticate the user with the Firebase authentication using the access token. */
        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (!task.isSuccessful()) {
                            LoginManager.getInstance().logOut();
                            hideProgress();
                            mIsAuthenticating = false;
                        }
                    }
                });
    }

    private void setFacebookUserIdAndName() {
        GraphRequest request = GraphRequest.newMeRequest(
                AccessToken.getCurrentAccessToken(),
                new GraphRequest.GraphJSONObjectCallback() {
                    @Override
                    public void onCompleted(JSONObject object, GraphResponse response) {
                        try {
                            mFacebookUserId =
                                    object.getLong(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_ID);
                            mName = object.getString(Constants.FACEBOOK_FIELD_GRAPH_REQUEST_NAME);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });

        Bundle parameters = new Bundle();
        parameters.putString(
                Constants.FACEBOOK_FIELD_GRAPH_REQUEST_FIELDS,
                Constants.FACEBOOK_FIELD_GRAPH_REQUEST_ID
                        + ","
                        + Constants.FACEBOOK_FIELD_GRAPH_REQUEST_NAME);
        request.setParameters(parameters);
        request.executeAsync();
    }

    private void hideProgress() {
        mProgressBar.setVisibility(View.INVISIBLE);
        if (mNetworkMonitor.isNetworkConnected()) {
            onNetworkConnected();
        } else {
            onNetworkDisconnected();
        }
    }

    @Override
    public void onNetworkConnected() {
        if (!mIsAuthenticating) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLoadingTextView.setVisibility(View.INVISIBLE);
                    mLoginButton.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    @Override
    public void onNetworkDisconnected() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLoadingTextView.setText(R.string.login_no_internet);
                mLoginButton.setVisibility(View.INVISIBLE);
                mLoadingTextView.setVisibility(View.VISIBLE);
            }
        });
    }


    private void startActivity(Class activity) {
        Intent intent = new Intent(LoginActivity.this, activity);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
        if (mNetworkMonitor != null) {
            mNetworkMonitor.detach(this);
        }
    }
}
