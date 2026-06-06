// Target location: app/src/main/java/com/example/bcalm/Login_Fragment.java
package com.example.bcalm;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class Login_Fragment extends Fragment {

    private FirebaseAuth mAuth;

    private ProgressBar progressBarLogin;
    private Button buttonLogin;
    private Button buttonRegister;

    public Login_Fragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        // Auto-login only if the user is signed in AND verified.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && currentUser.isEmailVerified()) {
            view.post(() -> Navigation.findNavController(view)
                    .navigate(R.id.action_login_Fragment_to_home_Fragment));
            return view;
        }

        EditText etEmail = view.findViewById(R.id.editTextTextEmailAddress);
        EditText etPassword = view.findViewById(R.id.editTextTextPassword);
        TextView tvForgotPassword = view.findViewById(R.id.tvForgotPassword);
        buttonLogin = view.findViewById(R.id.button_Login);
        buttonRegister = view.findViewById(R.id.button_register);
        progressBarLogin = view.findViewById(R.id.progressBarLogin);

        buttonLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(getContext(), "Please enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }

            loginUser(email, password, v);
        });

        tvForgotPassword.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            resetPassword(email);
        });

        buttonRegister.setOnClickListener(v ->
                Navigation.findNavController(v)
                        .navigate(R.id.action_login_Fragment_to_register_Fragment)
        );

        return view;
    }

    private void loginUser(String email, String password, View v) {
        setLoading(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();

                        if (user != null && !user.isEmailVerified()) {
                            // Resend the verification link and block entry until verified.
                            user.sendEmailVerification();
                            mAuth.signOut();
                            setLoading(false);
                            Toast.makeText(
                                    getContext(),
                                    "Please verify your email. A new verification link was sent.",
                                    Toast.LENGTH_LONG
                            ).show();
                            return;
                        }

                        setLoading(false);
                        Toast.makeText(getContext(), "Login Successful!", Toast.LENGTH_SHORT).show();

                        Navigation.findNavController(v)
                                .navigate(R.id.action_login_Fragment_to_home_Fragment);
                    } else {
                        setLoading(false);
                        String msg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Unknown error";
                        Toast.makeText(getContext(), "Login failed: " + msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void resetPassword(String email) {
        if (email.isEmpty()) {
            Toast.makeText(getContext(), "Enter your email address first", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        Toast.makeText(
                                getContext(),
                                "A password reset link was sent to your email",
                                Toast.LENGTH_LONG
                        ).show();
                    } else {
                        String msg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Unknown error";
                        Toast.makeText(getContext(), "Error: " + msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        if (progressBarLogin != null) {
            progressBarLogin.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (buttonLogin != null) {
            buttonLogin.setEnabled(!loading);
        }
        if (buttonRegister != null) {
            buttonRegister.setEnabled(!loading);
        }
    }
}
