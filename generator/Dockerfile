FROM python:3.11-slim
WORKDIR /gen
COPY requirements.txt /gen/requirements.txt
RUN pip install -r /gen/requirements.txt
COPY generator.py /gen/generator.py
CMD ["python", "generator.py"]
