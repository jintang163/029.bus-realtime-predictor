import os
import sys
import numpy as np
import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.metrics import mean_absolute_error, mean_squared_error


def generate_training_data(n_samples=50000, output_path="model/training_data.csv"):
    np.random.seed(42)
    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    current_speed = np.random.uniform(2, 18, n_samples)
    historical_speed = current_speed * np.random.uniform(0.7, 1.3, n_samples)
    congestion_factor = np.random.uniform(1.0, 5.0, n_samples)
    hour_of_day = np.random.randint(0, 24, n_samples)
    day_of_week = np.random.randint(1, 8, n_samples)

    speed_ratio = historical_speed / np.maximum(current_speed, 0.5)
    is_peak = ((7 <= hour_of_day) & (hour_of_day <= 9)) | ((17 <= hour_of_day) & (hour_of_day <= 19))
    is_weekend = day_of_week >= 6

    correction = 0.5 + 0.5 * speed_ratio
    correction += np.where(is_peak, 0.05, 0)
    correction += np.where(is_weekend, 0.02, 0)
    correction += (congestion_factor - 1.0) * 0.03
    correction += np.random.normal(0, 0.03, n_samples)
    correction = np.clip(correction, 0.7, 1.3)

    df = pd.DataFrame({
        "current_speed": current_speed,
        "historical_speed": historical_speed,
        "congestion_factor": congestion_factor,
        "hour_of_day": hour_of_day,
        "day_of_week": day_of_week,
        "speed_ratio": speed_ratio,
        "is_peak": is_peak.astype(int),
        "is_weekend": is_weekend.astype(int),
        "correction_factor": correction
    })

    df.to_csv(output_path, index=False)
    print(f"Generated {n_samples} training samples to {output_path}")
    return df


def train_model(data_path="model/training_data.csv", model_path="model/xgboost_arrival.json"):
    import xgboost as xgb

    if not os.path.exists(data_path):
        generate_training_data(output_path=data_path)

    df = pd.read_csv(data_path)
    feature_cols = ["current_speed", "historical_speed", "congestion_factor",
                    "hour_of_day", "day_of_week", "speed_ratio", "is_peak", "is_weekend"]

    X = df[feature_cols].values
    y = df["correction_factor"].values

    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

    params = {
        "objective": "reg:squarederror",
        "max_depth": 6,
        "learning_rate": 0.1,
        "n_estimators": 200,
        "subsample": 0.8,
        "colsample_bytree": 0.8,
        "min_child_weight": 5,
        "reg_alpha": 0.1,
        "reg_lambda": 1.0
    }

    model = xgb.XGBRegressor(**params)
    model.fit(X_train, y_train)

    y_pred = model.predict(X_test)
    mae = mean_absolute_error(y_test, y_pred)
    rmse = np.sqrt(mean_squared_error(y_test, y_pred))

    print(f"MAE: {mae:.4f}")
    print(f"RMSE: {rmse:.4f}")
    print(f"Predictions range: [{y_pred.min():.3f}, {y_pred.max():.3f}]")

    os.makedirs(os.path.dirname(model_path), exist_ok=True)
    model.get_booster().save_model(model_path)
    print(f"Model saved to {model_path}")

    return model


if __name__ == "__main__":
    train_model()
