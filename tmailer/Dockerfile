FROM python:3.12-slim
WORKDIR /app
COPY backend ./backend
COPY ui ./ui
ENV PORT=8080
EXPOSE 8080
CMD ["sh", "-c", "exec python3 backend/server.py --public --host 0.0.0.0 --port \"$PORT\" --no-browser"]
