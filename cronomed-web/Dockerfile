# Build do portal e entrega por nginx. Uso:
#   docker build -t cronomed-web .
#   docker run -p 3000:80 -e BACKEND_URL=http://host.docker.internal:8080 cronomed-web
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci || npm install
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
ENV BACKEND_URL=http://host.docker.internal:8080
COPY docker/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
