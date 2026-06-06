// Target location: app/src/main/java/com/example/bcalm/Register_Fragment.java
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
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class Register_Fragment extends Fragment {

    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    private ProgressBar progressBarRegister;
    private Button btnRegister;

    public Register_Fragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        mDatabase = FirebaseDatabase.getInstance().getReference();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_register_, container, false);

        EditText etEmail = view.findViewById(R.id.editTextTextEmailAddress2);
        EditText etPassword = view.findViewById(R.id.editTextTextPassword2);
        EditText etFullName = view.findViewById(R.id.editTextFullName);
        EditText etPhone = view.findViewById(R.id.editTextPhone);
        EditText etId = view.findViewById(R.id.editTextTextID);
        EditText etAge = view.findViewById(R.id.editTextAge);
        btnRegister = view.findViewById(R.id.button_registerr);
        progressBarRegister = view.findViewById(R.id.progressBarRegister);

        btnRegister.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String name = etFullName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();
            String id = etId.getText().toString().trim();
            String age = etAge.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty() || name.isEmpty()) {
                Toast.makeText(getContext(), "Please fill in all required fields", Toast.LENGTH_SHORT).show();
                return;
            }

            registerUser(email, password, name, phone, id, age, v);
        });

        return view;
    }

    private void registerUser(String email, String password, String name, String phone, String idNumber, String ageStr, View v) {
        setLoading(true);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();

                        if (user == null) {
                            setLoading(false);
                            Toast.makeText(getContext(), "Registration error: no user", Toast.LENGTH_LONG).show();
                            return;
                        }

                        writeUserToDb(user, email, phone, name, idNumber, ageStr, v);
                    } else {
                        setLoading(false);
                        String msg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Unknown error";
                        Toast.makeText(getContext(), "Registration failed: " + msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void writeUserToDb(FirebaseUser user, String email, String phone, String name, String idNumber, String ageStr, View v) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("fullName", name);
        userData.put("email", email);
        userData.put("phone", phone);
        userData.put("id", idNumber);
        userData.put("age", ageStr);

        mDatabase.child("users").child(user.getUid()).setValue(userData)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Send verification email, then sign out so the user must verify before logging in.
                        user.sendEmailVerification();
                        mAuth.signOut();

                        setLoading(false);
                        Toast.makeText(
                                getContext(),
                                "Account created! Check your email to verify it, then log in.",
                                Toast.LENGTH_LONG
                        ).show();

                        // Go back to the login screen.
                        Navigation.findNavController(v).popBackStack();
                    } else {
                        setLoading(false);
                        String msg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Unknown error";
                        Toast.makeText(getContext(), "Error saving data: " + msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        if (progressBarRegister != null) {
            progressBarRegister.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (btnRegister != null) {
            btnRegister.setEnabled(!loading);
        }
    }
}
