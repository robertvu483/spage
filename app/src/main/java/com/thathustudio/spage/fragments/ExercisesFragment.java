package com.thathustudio.spage.fragments;


import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.NinePatchDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.h6ah4i.android.widget.advrecyclerview.animator.GeneralItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.animator.SwipeDismissItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.decoration.ItemShadowDecorator;
import com.h6ah4i.android.widget.advrecyclerview.swipeable.RecyclerViewSwipeManager;
import com.h6ah4i.android.widget.advrecyclerview.touchguard.RecyclerViewTouchActionGuardManager;
import com.thathustudio.spage.R;
import com.thathustudio.spage.activities.QuestionsActivity;
import com.thathustudio.spage.app.CustomApplication;
import com.thathustudio.spage.exception.SpageException;
import com.thathustudio.spage.fragments.dialogs.ExerciseDetailsDialogFragment;
import com.thathustudio.spage.fragments.dialogs.ExerciseRankDialogFragment;
import com.thathustudio.spage.model.Exercise;
import com.thathustudio.spage.model.responses.EndPointResponse;
import com.thathustudio.spage.model.responses.Task4ListResponse;
import com.thathustudio.spage.service.retrofit.TranslateRetrofitException;
import com.thathustudio.spage.utils.ExerciseRecyclerViewAdapter;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * A simple {@link BaseFragment} subclass.
 * Use the {@link ExercisesFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ExercisesFragment extends BaseFragment implements ExerciseRecyclerViewAdapter.OnExerciseViewInteractionListener, SwipeRefreshLayout.OnRefreshListener, View.OnClickListener {
    private static final String USER_ID = "User ID";
    private static final String EXERCISES = "Exercises";
    private static final String DATA_INITIALIZED = "Data Initialized";
    private static final String HINT = "Hint";
    private ExerciseRecyclerViewAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayoutExercises;
    private boolean dataInitialized;
    private List<Call> calls;
    private boolean reloadable;
    private int userId;
    private Button buttonHint;

    public static ExercisesFragment newInstance(int userId) {
        ExercisesFragment fragment = new ExercisesFragment();
        Bundle args = new Bundle();
        args.putInt(USER_ID, userId);
        fragment.setArguments(args);
        return fragment;
    }

    private void recyclerViewInit(RecyclerView recyclerView) {
        // touch guard manager  (this class is required to suppress scrolling while swipe-dismiss animation is running)
        RecyclerViewTouchActionGuardManager touchActionGuardManager = new RecyclerViewTouchActionGuardManager();
        touchActionGuardManager.setInterceptVerticalScrollingWhileAnimationRunning(true);
        touchActionGuardManager.setEnabled(true);

        // swipe manager
        RecyclerViewSwipeManager swipeManager = new RecyclerViewSwipeManager();

        // adapter
        adapter = new ExerciseRecyclerViewAdapter(getContext(), this);
        RecyclerView.Adapter wrappedAdapter = swipeManager.createWrappedAdapter(adapter); // wrap for swiping

        // Change animations are enabled by default since support-v7-recyclerview v22.
        // Disable the change animation in order to make turning back animation of swiped item works properly.
        final GeneralItemAnimator animator = new SwipeDismissItemAnimator();
        animator.setSupportsChangeAnimations(false);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(wrappedAdapter);  // requires *wrapped* adapter
        recyclerView.setItemAnimator(animator);

        // additional decorations
        // Lollipop or later has native drop shadow feature. ItemShadowDecorator is not required.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            recyclerView.addItemDecoration(new ItemShadowDecorator((NinePatchDrawable) ContextCompat.getDrawable(getContext(), R.drawable.nine_patch_material_shadow_z1)));
        }

        // NOTE:
        // The initialization order is very important! This order determines the priority of touch event handling.
        //
        // priority: TouchActionGuard > Swipe > DragAndDrop
        touchActionGuardManager.attachRecyclerView(recyclerView);
        swipeManager.attachRecyclerView(recyclerView);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            userId = getArguments().getInt(USER_ID);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_exercises, container, false);

        dataInitialized = false;
        calls = new ArrayList<>();

        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.rclrV_exercises);
        recyclerViewInit(recyclerView);
        buttonHint = (Button) view.findViewById(R.id.btn_hint);
        buttonHint.setOnClickListener(this);
        swipeRefreshLayoutExercises = (SwipeRefreshLayout) view.findViewById(R.id.swpRfr_exercises);
        swipeRefreshLayoutExercises.setOnRefreshListener(this);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadable = false;
        swipeRefreshLayoutExercises.setRefreshing(false);
        if (!dataInitialized) {
            swipeRefreshLayoutExercises.setRefreshing(true);
            onRefresh();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        reloadable = true;

        for (Call call : calls) {
            call.cancel();
        }
        calls.clear();
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(HINT, buttonHint.getVisibility());
        outState.putBoolean(DATA_INITIALIZED, dataInitialized);
        if (dataInitialized) {
            outState.putParcelableArrayList(EXERCISES, new ArrayList<>(adapter.getExercises()));
        }
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            //noinspection WrongConstant
            buttonHint.setVisibility(savedInstanceState.getInt(HINT, View.GONE));
            dataInitialized = savedInstanceState.getBoolean(DATA_INITIALIZED, false);
            if (dataInitialized) {
                List<Exercise> exercises = savedInstanceState.getParcelableArrayList(EXERCISES);
                adapter.replaceExercises(exercises);
            }
        }
    }

    @Override
    public void onExerciseInfoClick(Exercise exercise) {
        ExerciseDetailsDialogFragment.newInstance(exercise).show(getChildFragmentManager(), ExerciseDetailsDialogFragment.EXERCISE_DETAILS);
    }

    @Override
    public void onExerciseStartClick(Exercise exercise) {
        adapter.unpinPinnedExercise();
        Intent intent = new Intent(getContext().getApplicationContext(), QuestionsActivity.class);
        Bundle bundle = new Bundle();
        bundle.putInt(QuestionsActivity.USER_ID, userId);
        bundle.putParcelable(QuestionsActivity.EXERCISE, exercise);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    @Override
    public void onExerciseDownloadClick(Exercise exercise) {
        // TODO: download questions for exercise
    }

    @Override
    public void onExerciseRankClick(Exercise exercise) {
        ExerciseRankDialogFragment.newInstance(exercise.getId()).show(getChildFragmentManager(), ExerciseRankDialogFragment.EXERCISE_RANK);
    }

    @Override
    public void onRefresh() {
        CustomApplication customApplication = (CustomApplication) getActivity().getApplication();
        Call<Task4ListResponse<Exercise>> exerciseListResponseCall = customApplication.getTask4Service().getExercises();
        exerciseListResponseCall.enqueue(new GetExercisesCallback(this));
        calls.add(exerciseListResponseCall);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_hint:
                v.setVisibility(View.GONE);
                break;
        }
    }

    public static class GetExercisesCallback extends ExercisesFragmentCallback<Task4ListResponse<Exercise>> {
        public GetExercisesCallback(ExercisesFragment exercisesFragment) {
            super(exercisesFragment);
        }

        @Override
        public void onResponse(Call<Task4ListResponse<Exercise>> call, Response<Task4ListResponse<Exercise>> response) {
            ExercisesFragment exercisesFragment = weakReferenceExercisesFragment.get();
            if (exercisesFragment != null) {
                SpageException spageException = TranslateRetrofitException.translateServiceException(call, response);

                try {
                    exercisesFragment.swipeRefreshLayoutExercises.setRefreshing(false);
                    if (spageException != null) {
                        showToast(exercisesFragment.getContext().getApplicationContext(), spageException); // Fix warning
                        return;
                    }

                    /*// Content gernerator
                    Lorem lorem = LoremIpsum.getInstance();
                    Task4Service temp1 = ((CustomApplication) exercisesFragment.getActivity().getApplication()).getTask4Service();
                    Callback<Task4Response<Integer>> temp3 = new Callback<Task4Response<Integer>>() {
                        @Override
                        public void onResponse(Call<Task4Response<Integer>> call, Response<Task4Response<Integer>> response) {

                        }

                        @Override
                        public void onFailure(Call<Task4Response<Integer>> call, Throwable t) {

                        }
                    };
                    for (Exercise exercise : response.body().getResponse())
                    {
                        exercise.setContent(lorem.getWords(10, 50));
                        Call<Task4Response<Integer>> temp2 = temp1.putExercise(exercise.getId(), exercise);
                        temp2.enqueue(temp3);
                    }
                    //----------------*/

                    exercisesFragment.adapter.replaceExercises(response.body().getResponse());
                    exercisesFragment.dataInitialized = true;
                } catch (Exception ex) {
                    Log.e("SPage", ex.getMessage());
                } finally {
                    exercisesFragment.calls.remove(call);
                }
            }
        }
    }

    public static abstract class ExercisesFragmentCallback<T extends EndPointResponse> implements Callback<T> {
        protected final WeakReference<ExercisesFragment> weakReferenceExercisesFragment;

        public ExercisesFragmentCallback(ExercisesFragment exercisesFragment) {
            weakReferenceExercisesFragment = new WeakReference<>(exercisesFragment);
        }

        protected void showToast(Context context, Throwable throwable) {
            Toast.makeText(context, throwable.getMessage(), Toast.LENGTH_LONG).show();
        }

        @Override
        public void onFailure(Call<T> call, Throwable t) {
            ExercisesFragment exercisesFragment = weakReferenceExercisesFragment.get();
            if (exercisesFragment != null) {
                try {
                    // Handle
                    exercisesFragment.swipeRefreshLayoutExercises.setRefreshing(false);
                    if (!exercisesFragment.reloadable) {
                        showToast(exercisesFragment.getContext().getApplicationContext(), t);
                    }
                } catch (Exception ex) {
                    Log.e("SPage", ex.getMessage());
                } finally {
                    exercisesFragment.calls.remove(call);
                }
            }
        }
    }
}
