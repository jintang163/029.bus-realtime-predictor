import grpc
from concurrent import futures
import logging
import os
import numpy as np

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class XGBoostPredictor:
    def __init__(self, model_path=None):
        self.model = None
        self.model_path = model_path or os.environ.get("MODEL_PATH", "model/xgboost_arrival.json")
        self._load_model()

    def _load_model(self):
        try:
            import xgboost as xgb
            if os.path.exists(self.model_path):
                self.model = xgb.Booster()
                self.model.load_model(self.model_path)
                logger.info("XGBoost model loaded from %s", self.model_path)
            else:
                logger.warning("Model file not found at %s, using fallback", self.model_path)
                self.model = None
        except ImportError:
            logger.warning("xgboost not installed, using fallback")
            self.model = None
        except Exception as e:
            logger.warning("Failed to load model: %s, using fallback", e)
            self.model = None

    def predict(self, segment_id, current_speed, historical_speed,
                congestion_factor, hour_of_day, day_of_week):
        if self.model is not None:
            try:
                import xgboost as xgb
                features = np.array([[
                    current_speed,
                    historical_speed,
                    congestion_factor,
                    hour_of_day,
                    day_of_week,
                    current_speed / max(historical_speed, 0.5),
                    1 if 7 <= hour_of_day <= 9 else (1 if 17 <= hour_of_day <= 19 else 0),
                    1 if day_of_week >= 6 else 0
                ]])
                dmatrix = xgb.DMatrix(features)
                prediction = self.model.predict(dmatrix)[0]
                correction = float(np.clip(prediction, 0.7, 1.3))
                confidence = 0.9
                return correction, confidence
            except Exception as e:
                logger.warning("XGBoost prediction failed: %s, using fallback", e)

        return self._fallback_predict(
            current_speed, historical_speed, congestion_factor,
            hour_of_day, day_of_week
        )

    def _fallback_predict(self, current_speed, historical_speed,
                          congestion_factor, hour_of_day, day_of_week):
        ratio = historical_speed / max(current_speed, 0.5) if current_speed > 0.5 else 1.0
        time_penalty = 1.05 if (7 <= hour_of_day <= 9 or 17 <= hour_of_day <= 19) else 1.0
        weekend_factor = 1.02 if day_of_week >= 6 else 1.0
        congestion_adj = 1.0 + (congestion_factor - 1.0) * 0.1

        correction = 0.5 + 0.5 * ratio * time_penalty * weekend_factor * congestion_adj
        correction = max(0.7, min(1.3, correction))
        confidence = 0.6
        return correction, confidence


class PredictServiceServicer:
    def __init__(self):
        self.predictor = XGBoostPredictor()

    def PredictCorrection(self, request, context):
        correction, confidence = self.predictor.predict(
            request.segment_id,
            request.current_speed,
            request.historical_speed,
            request.congestion_factor,
            request.hour_of_day,
            request.day_of_week
        )

        import predict_service_pb2 as pb2
        return pb2.PredictResponse(
            correction_factor=correction,
            confidence=confidence
        )


def serve():
    import predict_service_pb2_grpc as pb2_grpc

    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    pb2_grpc.add_PredictServiceServicer_to_server(
        PredictServiceServicer(), server
    )
    port = int(os.environ.get("GRPC_PORT", "50051"))
    server.add_insecure_port(f"[::]:{port}")
    server.start()
    logger.info("Predict gRPC service started on port %d", port)
    server.wait_for_termination()


if __name__ == "__main__":
    serve()
