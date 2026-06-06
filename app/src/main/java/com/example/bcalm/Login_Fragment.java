package com.example.bcalm;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;

public class Login_Fragment extends Fragment {

    private FirebaseAuth mAuth;

    public Login_Fragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        View view = inflater.inflate(R.layout.fragment_login, container, false);

        if (mAuth.getCurrentUser() != null) {
            view.post(() -> {
                Navigation.findNavController(view)
                        .navigate(R.id.action_login_Fragment_to_home_Fragment);
            });
            return view;
        }

        EditText etEmail = view.findViewById(R.id.editTextTextEmailAddress);
        EditText etPassword = view.findViewById(R.id.editTextTextPassword);
        Button buttonlogin = view.findViewById(R.id.button_Login);
        Button buttonregister = view.findViewById(R.id.button_register);

        buttonlogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(getContext(), "Please enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }

            loginUser(email, password, v);
        });

        buttonregister.setOnClickListener(v ->
                Navigation.findNavController(v)
                        .navigate(R.id.action_login_Fragment_to_register_Fragment)
        );

        return view;
    }

    private void loginUser(String email, String password, View v) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(getContext(), "Login Successful!", Toast.LENGTH_SHORT).show();

                        Navigation.findNavController(v)
                                .navigate(R.id.action_login_Fragment_to_home_Fragment);
                    } else {
                        Toast.makeText(
                                getContext(),
                                "Login failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }
}