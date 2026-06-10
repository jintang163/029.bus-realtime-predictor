FROM python:3.11-slim

WORKDIR /app

RUN pip install --no-cache-dir grpcio grpcio-tools xgboost numpy pandas scikit-learn

COPY proto/ ./proto/
RUN python -m grpc_tools.protoc \
    -I./proto \
    --python_out=. \
    --grpc_python_out=. \
    proto/predict_service.proto

COPY server.py .
COPY train_model.py .

RUN mkdir -p model && python train_model.py

EXPOSE 50051

CMD ["python", "server.py"]
